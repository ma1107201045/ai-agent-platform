package com.agent.platform.service.eval;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.eval.EvalDataset;
import com.agent.platform.dao.entity.eval.EvalRun;
import com.agent.platform.dao.entity.eval.EvalRunCase;
import com.agent.platform.dao.entity.eval.EvalSample;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.mapper.eval.EvalDatasetMapper;
import com.agent.platform.dao.mapper.eval.EvalRunCaseMapper;
import com.agent.platform.dao.mapper.eval.EvalRunMapper;
import com.agent.platform.dao.mapper.eval.EvalSampleMapper;
import com.agent.platform.dao.mapper.model.ModelInfoMapper;
import com.agent.platform.dao.vo.eval.EvalRunVO;
import com.agent.platform.service.app.AppAgentService;
import com.agent.platform.service.common.AppExecuteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 评测任务服务：创建任务后在线程池中顺序跑完数据集样本，
 * 自动生成通过率 / 平均得分 / 耗时报告，支持中断与重跑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalRunService implements DisposableBean {

    private final EvalRunMapper runMapper;
    private final EvalRunCaseMapper caseMapper;
    private final EvalDatasetMapper datasetMapper;
    private final EvalSampleMapper sampleMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final AppAgentService appAgentService;
    private final AppExecuteService appExecuteService;
    private final ObjectMapper objectMapper;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Override
    public void destroy() {
        pool.shutdownNow();
    }

    // ---------- 任务 CRUD ----------

    public Page<EvalRunVO> page(long page, long size, String status, Long datasetId, Long experimentId, String keyword) {
        Page<EvalRun> p = runMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EvalRun>()
                        .eq(EvalRun::getTenantId, tenant())
                        .eq(StringUtils.hasText(status), EvalRun::getStatus, status)
                        .eq(datasetId != null, EvalRun::getDatasetId, datasetId)
                        .eq(experimentId != null, EvalRun::getExperimentId, experimentId)
                        .and(StringUtils.hasText(keyword), w -> w.like(EvalRun::getName, keyword))
                        .orderByDesc(EvalRun::getId));
        Page<EvalRunVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        for (EvalRun run : p.getRecords()) {
            result.getRecords().add(toVO(run));
        }
        return result;
    }

    public EvalRunVO get(Long id) {
        EvalRun run = runMapper.selectById(id);
        if (run == null) {
            throw new BizException("评测任务不存在: " + id);
        }
        return toVO(run);
    }

    /** 评测中心聚合统计 */
    public Map<String, Object> stats() {
        Long total = runMapper.selectCount(new LambdaQueryWrapper<EvalRun>().eq(EvalRun::getTenantId, tenant()));
        Long running = runMapper.selectCount(new LambdaQueryWrapper<EvalRun>()
                .eq(EvalRun::getTenantId, tenant()).in(EvalRun::getStatus, "pending", "running"));
        Long failed = runMapper.selectCount(new LambdaQueryWrapper<EvalRun>()
                .eq(EvalRun::getTenantId, tenant()).eq(EvalRun::getStatus, "failed"));
        List<EvalRun> runs = runMapper.selectList(new LambdaQueryWrapper<EvalRun>()
                .eq(EvalRun::getTenantId, tenant())
                .eq(EvalRun::getStatus, "success")
                .orderByDesc(EvalRun::getId).last("LIMIT 10"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("running", running == null ? 0 : running);
        result.put("failed", failed == null ? 0 : failed);
        // 最近成功任务的平均通过率（趋势用）
        List<Map<String, Object>> recent = new ArrayList<>();
        for (EvalRun run : runs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", run.getId());
            item.put("name", run.getName());
            item.put("passRate", run.getPassRate() == null ? null : run.getPassRate().doubleValue());
            item.put("avgScore", run.getAvgScore() == null ? null : run.getAvgScore().doubleValue());
            item.put("finishedAt", run.getFinishedAt());
            recent.add(item);
        }
        result.put("recent", recent);
        return result;
    }

    /**
     * 创建并立即运行评测任务。
     * body: name/datasetId/experimentId(可选)/(appId + appVersionId 可选 或 modelId)
     */
    public EvalRunVO createAndRun(EvalRun req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException("任务名称不能为空");
        }
        EvalDataset dataset = datasetMapper.selectById(req.getDatasetId());
        if (dataset == null) {
            throw new BizException("评测数据集不存在");
        }
        boolean byApp = req.getAppId() != null;
        boolean byModel = req.getModelId() != null;
        if (byApp == byModel) {
            throw new BizException("请选择被测对象：应用（appId）或直连模型（modelId），二者选一");
        }
        Long active = sampleMapper.selectCount(new LambdaQueryWrapper<EvalSample>()
                .eq(EvalSample::getDatasetId, req.getDatasetId())
                .eq(EvalSample::getStatus, 1));
        if (active == null || active == 0) {
            throw new BizException("数据集没有可用样本，请先录入样本");
        }
        LocalDateTime now = LocalDateTime.now();
        EvalRun run = new EvalRun();
        run.setTenantId(tenant());
        run.setName(req.getName().trim());
        run.setExperimentId(req.getExperimentId());
        run.setDatasetId(req.getDatasetId());
        run.setAppId(req.getAppId());
        run.setAppVersionId(req.getAppVersionId());
        run.setModelId(req.getModelId());
        run.setStatus("pending");
        run.setTotalCount(0);
        run.setSuccessCount(0);
        run.setFailedCount(0);
        run.setCreatedBy(UserContext.getUserId());
        run.setCreateTime(now);
        runMapper.insert(run);
        pool.submit(() -> execute(run.getId()));
        return get(run.getId());
    }

    /** 重跑已结束/失败的任务 */
    public EvalRunVO rerun(Long id) {
        EvalRun run = mustGet(id);
        if ("running".equals(run.getStatus()) || "pending".equals(run.getStatus())) {
            throw new BizException("任务运行中，不能重复启动");
        }
        caseMapper.delete(new LambdaQueryWrapper<EvalRunCase>().eq(EvalRunCase::getRunId, id));
        run.setStatus("pending");
        run.setTotalCount(0);
        run.setSuccessCount(0);
        run.setFailedCount(0);
        run.setPassRate(null);
        run.setAvgScore(null);
        run.setReportJson(null);
        run.setStartedAt(null);
        run.setFinishedAt(null);
        run.setError(null);
        runMapper.updateById(run);
        pool.submit(() -> execute(run.getId()));
        return get(id);
    }

    /** 停止运行中的任务（worker 会在下个样本前退出） */
    public void stop(Long id) {
        EvalRun run = mustGet(id);
        if ("running".equals(run.getStatus()) || "pending".equals(run.getStatus())) {
            run.setStatus("stopped");
            run.setFinishedAt(LocalDateTime.now());
            run.setError("用户手动停止");
            runMapper.updateById(run);
        }
    }

    public void delete(Long id) {
        mustGet(id);
        caseMapper.delete(new LambdaQueryWrapper<EvalRunCase>().eq(EvalRunCase::getRunId, id));
        runMapper.deleteById(id);
    }

    // ---------- 执行 ----------

    private void execute(Long runId) {
        try {
            EvalRun run = runMapper.selectById(runId);
            if (run == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            run.setStatus("running");
            run.setStartedAt(now);
            run.setFinishedAt(null);
            run.setError(null);
            runMapper.updateById(run);

            List<EvalSample> samples = sampleMapper.selectList(new LambdaQueryWrapper<EvalSample>()
                    .eq(EvalSample::getDatasetId, run.getDatasetId())
                    .eq(EvalSample::getStatus, 1)
                    .orderByAsc(EvalSample::getId));

            int total = 0;
            int passedCount = 0;
            List<BigDecimal> scores = new ArrayList<>();
            List<Long> latencies = new ArrayList<>();
            Map<String, long[]> categoryStats = new LinkedHashMap<>();

            for (EvalSample sample : samples) {
                // 每跑若干条重新读取状态，支持手动停止
                if ((total & 15) == 0) {
                    EvalRun fresh = runMapper.selectById(runId);
                    if (fresh == null || "stopped".equals(fresh.getStatus())) {
                        break;
                    }
                }
                total++;
                EvalRunCase c = new EvalRunCase();
                c.setRunId(runId);
                c.setSampleId(sample.getId());
                c.setQuestion(sample.getQuestion());
                c.setReference(sample.getReference());

                long start = System.currentTimeMillis();
                try {
                    AppExecuteService.Reply reply;
                    if (run.getModelId() != null) {
                        reply = appExecuteService.runModel(run.getModelId(), sample.getQuestion());
                    } else {
                        reply = appExecuteService.runApp(run.getAppId(), sample.getQuestion());
                    }
                    c.setAnswer(reply.answer());
                    c.setLatencyMs((int) (System.currentTimeMillis() - start));
                } catch (Exception e) {
                    c.setAnswer(null);
                    c.setLatencyMs((int) (System.currentTimeMillis() - start));
                    c.setError(trim(e.getMessage(), 800));
                }
                scoreCase(c);
                if (c.getPassed() != null && c.getPassed() == 1) {
                    passedCount++;
                }
                if (c.getScore() != null) {
                    scores.add(c.getScore());
                }
                latencies.add(c.getLatencyMs() == null ? 0L : c.getLatencyMs().longValue());
                if (StringUtils.hasText(sample.getCategory())) {
                    long[] arr = categoryStats.computeIfAbsent(sample.getCategory(), k -> new long[2]);
                    arr[0]++;
                    arr[1] += (c.getPassed() != null && c.getPassed() == 1) ? 1 : 0;
                }
                c.setCreateTime(LocalDateTime.now());
                caseMapper.insert(c);

                EvalRun upd = new EvalRun();
                upd.setId(runId);
                upd.setTotalCount(total);
                upd.setSuccessCount(passedCount);
                upd.setFailedCount(total - passedCount);
                runMapper.updateById(upd);
            }

            EvalRun finish = runMapper.selectById(runId);
            if (finish == null) {
                return;
            }
            finish.setTotalCount(total);
            finish.setSuccessCount(passedCount);
            finish.setFailedCount(total - passedCount);
            finish.setStatus("success");
            finish.setFinishedAt(LocalDateTime.now());
            if (total > 0) {
                finish.setPassRate(BigDecimal.valueOf((double) passedCount / total).setScale(4, RoundingMode.HALF_UP));
                if (!scores.isEmpty()) {
                    double sum = scores.stream().mapToDouble(BigDecimal::doubleValue).sum();
                    finish.setAvgScore(BigDecimal.valueOf(sum / scores.size()).setScale(4, RoundingMode.HALF_UP));
                }
            }
            Map<String, Object> report = new LinkedHashMap<>();
            double avgLatency = latencies.isEmpty() ? 0 : latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            double maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
            report.put("avgLatencyMs", Math.round(avgLatency));
            report.put("maxLatencyMs", Math.round(maxLatency));
            Map<String, Object> cats = new LinkedHashMap<>();
            categoryStats.forEach((k, v) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("total", v[0]);
                m.put("passed", v[1]);
                m.put("passRate", v[0] == 0 ? null : BigDecimal.valueOf((double) v[1] / v[0]).setScale(4, RoundingMode.HALF_UP));
                cats.put(k, m);
            });
            report.put("categories", cats);
            finish.setReportJson(writeJson(report));
            runMapper.updateById(finish);
        } catch (Exception e) {
            log.error("评测任务执行异常 runId={}", runId, e);
            EvalRun fail = new EvalRun();
            fail.setId(runId);
            fail.setStatus("failed");
            fail.setFinishedAt(LocalDateTime.now());
            fail.setError(trim(e.getMessage(), 1000));
            runMapper.updateById(fail);
        }
    }

    /** 打分：无参考答案视为通过（可执行性）；有参考答案按字符重合度打分 */
    private void scoreCase(EvalRunCase c) {
        String answer = c.getAnswer();
        String reference = c.getReference();
        if (!StringUtils.hasText(reference)) {
            c.setPassed(StringUtils.hasText(answer) ? 1 : 0);
            c.setScore(null);
            return;
        }
        if (!StringUtils.hasText(answer)) {
            c.setPassed(0);
            c.setScore(BigDecimal.ZERO.setScale(4));
            return;
        }
        double score = diceSimilarity(answer, reference);
        c.setScore(BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
        c.setPassed(score >= 0.45 ? 1 : 0);
    }

    /** 字符 bigram Dice 相似度；短文本退化为包含关系 */
    private double diceSimilarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return 0;
        }
        if (na.length() <= 20 && na.contains(nb) || nb.length() <= 20 && nb.contains(na)) {
            return 1;
        }
        if (na.equals(nb)) {
            return 1;
        }
        java.util.Set<String> gramsA = grams(na);
        java.util.Set<String> gramsB = grams(nb);
        if (gramsA.isEmpty() || gramsB.isEmpty()) {
            return 0;
        }
        int common = 0;
        for (String g : gramsA) {
            if (gramsB.contains(g)) {
                common++;
            }
        }
        return 2.0 * common / (gramsA.size() + gramsB.size());
    }

    private java.util.Set<String> grams(String s) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        if (set.isEmpty() && !s.isEmpty()) {
            set.add(s);
        }
        return set;
    }

    private String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ---------- helpers ----------

    private EvalRunVO toVO(EvalRun run) {
        EvalRunVO vo = new EvalRunVO();
        BeanUtils.copyProperties(run, vo);
        EvalDataset dataset = datasetMapper.selectById(run.getDatasetId());
        vo.setDatasetName(dataset == null ? null : dataset.getName());
        if (run.getAppId() != null) {
            AppAgent app = appAgentService.getById(run.getAppId());
            if (app != null) {
                vo.setAppName(app.getName());
                vo.setAppType(app.getType());
            }
        }
        if (run.getModelId() != null) {
            ModelInfo model = modelInfoMapper.selectById(run.getModelId());
            vo.setModelName(model == null ? null : model.getName());
        }
        return vo;
    }

    private EvalRun mustGet(Long id) {
        EvalRun run = runMapper.selectById(id);
        if (run == null) {
            throw new BizException("评测任务不存在: " + id);
        }
        return run;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
