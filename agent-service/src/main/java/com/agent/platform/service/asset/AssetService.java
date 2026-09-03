package com.agent.platform.service.asset;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.asset.AssetFile;
import com.agent.platform.dao.mapper.asset.AssetFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 素材管理服务：文件上传落盘 + 元数据管理 + 内容读取。
 *
 * <p>文件存储于配置目录（platform.upload-dir），默认 {@code ./data/uploads}，
 * 存储路径按日期分目录并以 UUID 命名，避免重名与路径穿越。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private static final Set<String> DOC_EXTS = Set.of(
            "txt", "md", "markdown", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "json", "html");
    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final AssetFileMapper assetFileMapper;

    @Value("${platform.upload-dir:./data/uploads}")
    private String uploadDir;

    // ---------- 上传 ----------

    public AssetFile upload(MultipartFile file, String name, String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            original = "未命名文件";
        }
        String ext = extensionOf(original);
        String contentType = file.getContentType();
        String realCategory = normalizeCategory(category, contentType, ext);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String relPath = LocalDate.now().format(DIR_FORMAT) + "/" + uuid + (ext.isBlank() ? "" : "." + ext);

        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(relPath).normalize();
        if (!target.startsWith(root)) {
            throw new BizException("非法的存储路径");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("素材文件写入失败 name={}", original, e);
            throw new BizException("文件保存失败: " + e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        AssetFile asset = new AssetFile();
        asset.setTenantId(1L);
        asset.setName(StringUtils.hasText(name) ? name.trim() : stripExt(original));
        asset.setOriginalName(original);
        asset.setExt(ext);
        asset.setContentType(contentType);
        asset.setSize(file.getSize());
        asset.setCategory(realCategory);
        asset.setStoragePath(relPath);
        asset.setStatus(1);
        asset.setCreatedBy(1L);
        asset.setCreateTime(now);
        asset.setUpdateTime(now);
        assetFileMapper.insert(asset);
        return asset;
    }

    // ---------- 列表 / 元数据 ----------

    public Page<AssetFile> page(long page, long size, String category, String keyword) {
        LambdaQueryWrapper<AssetFile> wrapper = new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getStatus, 1)
                .eq(StringUtils.hasText(category), AssetFile::getCategory, category)
                .orderByDesc(AssetFile::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AssetFile::getName, kw)
                    .or().like(AssetFile::getOriginalName, kw));
        }
        return assetFileMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public AssetFile getAsset(Long id) {
        AssetFile asset = assetFileMapper.selectById(id);
        if (asset == null || asset.getStatus() == null || asset.getStatus() != 1) {
            throw new BizException("素材不存在: " + id);
        }
        return asset;
    }

    public AssetFile updateMeta(Long id, String name, String category, Integer status) {
        AssetFile asset = getAsset(id);
        if (name != null && StringUtils.hasText(name)) {
            asset.setName(name.trim());
        }
        if (StringUtils.hasText(category)) {
            asset.setCategory(normalizeCategory(category, asset.getContentType(), asset.getExt()));
        }
        if (status != null) {
            asset.setStatus(status);
        }
        asset.setUpdateTime(LocalDateTime.now());
        assetFileMapper.updateById(asset);
        return asset;
    }

    public void delete(Long id) {
        AssetFile asset = assetFileMapper.selectById(id);
        if (asset == null) {
            return;
        }
        assetFileMapper.deleteById(id);
        deleteFileQuietly(asset.getStoragePath());
    }

    /** 读取文件字节（用于下载/预览） */
    public byte[] readContent(Long id) {
        AssetFile asset = getAsset(id);
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(asset.getStoragePath()).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new BizException("文件内容不存在，可能已被移动或删除");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.error("读取素材文件失败 id={}", id, e);
            throw new BizException("文件读取失败: " + e.getMessage());
        }
    }

    // ---------- 私有工具 ----------

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /** 校验并归一化分类；为空时根据 MIME/扩展名推断 */
    private String normalizeCategory(String category, String contentType, String ext) {
        if (StringUtils.hasText(category)) {
            String c = category.trim().toLowerCase();
            if (Set.of("image", "document", "audio", "video", "other").contains(c)) {
                return c;
            }
            throw new BizException("不支持的素材分类: " + category);
        }
        if (contentType != null) {
            if (contentType.startsWith("image/")) return "image";
            if (contentType.startsWith("audio/")) return "audio";
            if (contentType.startsWith("video/")) return "video";
        }
        if (DOC_EXTS.contains(ext)) return "document";
        return "other";
    }

    private void deleteFileQuietly(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }
        try {
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            Path target = root.resolve(storagePath).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("删除素材文件失败 storagePath={}: {}", storagePath, e.getMessage());
        }
    }
}
