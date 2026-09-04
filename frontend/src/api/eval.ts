import request from './request'
import type { PageResult } from './types'

/** 评测数据集 */
export interface EvalDataset {
  id: number
  name: string
  description?: string
  source: 'manual' | 'import' | 'feedback'
  sampleCount: number
  status: number
  createTime?: string
  updateTime?: string
}

/** 评测样本 */
export interface EvalSample {
  id: number
  datasetId: number
  question: string
  reference?: string
  category?: string
  status: number
  createTime?: string
}

/** 评测任务（含名称增强） */
export interface EvalRun {
  id: number
  experimentId?: number
  name?: string
  datasetId: number
  appId?: number
  appVersionId?: number
  modelId?: number
  status: 'pending' | 'running' | 'success' | 'failed' | 'stopped'
  totalCount: number
  successCount: number
  failedCount: number
  passRate?: number
  avgScore?: number
  reportJson?: string
  startedAt?: string
  finishedAt?: string
  error?: string
  createTime?: string
  datasetName?: string
  appName?: string
  appType?: string
  modelName?: string
}

/** 评测用例明细 */
export interface EvalRunCase {
  id: number
  runId: number
  sampleId?: number
  question: string
  reference?: string
  answer?: string
  passed: number
  score?: number
  latencyMs?: number
  error?: string
  createTime?: string
}

export interface EvalReport {
  avgLatencyMs?: number
  maxLatencyMs?: number
  categories?: Record<string, { total: number; passed: number; passRate?: number }>
}

/** 评测中心统计 */
export interface EvalStats {
  total: number
  running: number
  failed: number
  recent: { id: number; name?: string; passRate?: number; avgScore?: number; finishedAt?: string }[]
}

/** 对比实验 */
export interface EvalExperiment {
  id: number
  name: string
  description?: string
  datasetId: number
  status: number
  createTime?: string
  updateTime?: string
}

export const evalApi = {
  // ---------- 数据集 ----------
  datasetPage(params: { page?: number; size?: number; keyword?: string; status?: number }) {
    return request.get<never, PageResult<EvalDataset>>('/eval/datasets', { params })
  },
  datasetOptions() {
    return request.get<never, EvalDataset[]>('/eval/datasets/options')
  },
  createDataset(data: { name: string; description?: string; source?: string }) {
    return request.post<never, EvalDataset>('/eval/datasets', data)
  },
  updateDataset(id: number, data: { name?: string; description?: string; status?: number }) {
    return request.put<never, void>(`/eval/datasets/${id}`, data)
  },
  removeDataset(id: number) {
    return request.delete<never, void>(`/eval/datasets/${id}`)
  },
  samplePage(datasetId: number, params: { page?: number; size?: number; keyword?: string }) {
    return request.get<never, PageResult<EvalSample>>(`/eval/datasets/${datasetId}/samples`, { params })
  },
  addSample(datasetId: number, data: { question: string; reference?: string; category?: string }) {
    return request.post<never, EvalSample>(`/eval/datasets/${datasetId}/samples`, data)
  },
  updateSample(datasetId: number, sampleId: number, data: Partial<EvalSample>) {
    return request.put<never, void>(`/eval/datasets/${datasetId}/samples/${sampleId}`, data)
  },
  removeSample(datasetId: number, sampleId: number) {
    return request.delete<never, void>(`/eval/datasets/${datasetId}/samples/${sampleId}`)
  },
  importSamples(datasetId: number, text: string) {
    return request.post<never, { imported: number }>(`/eval/datasets/${datasetId}/samples/import`, { text })
  },

  // ---------- 评测任务 ----------
  runPage(params: { page?: number; size?: number; status?: string; datasetId?: number; experimentId?: number; keyword?: string }) {
    return request.get<never, PageResult<EvalRun>>('/eval/runs', { params })
  },
  runStats() {
    return request.get<never, EvalStats>('/eval/runs/stats')
  },
  createRun(data: { name: string; datasetId: number; appId?: number; appVersionId?: number; modelId?: number; experimentId?: number }) {
    return request.post<never, EvalRun>('/eval/runs', data)
  },
  rerun(id: number) {
    return request.post<never, EvalRun>(`/eval/runs/${id}/rerun`)
  },
  stop(id: number) {
    return request.post<never, void>(`/eval/runs/${id}/stop`)
  },
  removeRun(id: number) {
    return request.delete<never, void>(`/eval/runs/${id}`)
  },
  cases(runId: number, params: { page?: number; size?: number; passed?: number; keyword?: string }) {
    return request.get<never, PageResult<EvalRunCase>>(`/eval/runs/${runId}/cases`, { params })
  },

  // ---------- 对比实验 ----------
  experimentPage(params: { page?: number; size?: number; keyword?: string; status?: number }) {
    return request.get<never, PageResult<EvalExperiment>>('/eval/experiments', { params })
  },
  createExperiment(data: { name: string; description?: string; datasetId: number }) {
    return request.post<never, EvalExperiment>('/eval/experiments', data)
  },
  updateExperiment(id: number, data: { name?: string; description?: string; datasetId?: number; status?: number }) {
    return request.put<never, void>(`/eval/experiments/${id}`, data)
  },
  removeExperiment(id: number) {
    return request.delete<never, void>(`/eval/experiments/${id}`)
  }
}

/** 将 reportJson 解析为结构化报告 */
export function parseReport(run?: Pick<EvalRun, 'reportJson'>): EvalReport | null {
  if (!run?.reportJson) return null
  try {
    return JSON.parse(run.reportJson) as EvalReport
  } catch {
    return null
  }
}
