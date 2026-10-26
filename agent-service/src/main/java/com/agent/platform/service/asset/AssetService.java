package com.agent.platform.service.asset;

import com.agent.platform.dao.entity.asset.AssetFile;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

/**
 * 素材管理服务：文件上传落盘 + 元数据管理 + 内容读取。
 *
 * <p>文件存储于配置目录（platform.upload-dir），默认 {@code ./data/uploads}，
 * 存储路径按日期分目录并以 UUID 命名，避免重名与路径穿越。</p>
 */
public interface AssetService {

    AssetFile upload(MultipartFile file, String name, String category);

    Page<AssetFile> page(long page, long size, String category, String keyword);

    AssetFile getAsset(Long id);

    AssetFile updateMeta(Long id, String name, String category, Integer status);

    void delete(Long id);

    /** 读取文件字节（用于下载/预览） */
    byte[] readContent(Long id);
}
