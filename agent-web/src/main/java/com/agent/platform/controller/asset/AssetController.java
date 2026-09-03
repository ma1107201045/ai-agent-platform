package com.agent.platform.controller.asset;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.asset.AssetUpdateDTO;
import com.agent.platform.dao.entity.asset.AssetFile;
import com.agent.platform.service.asset.AssetService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材管理接口：文件上传 / 元数据管理 / 内容读取
 *
 * <p>URL：/api/assets/*（upload-dir 见 application.yml 的 platform.upload-dir）</p>
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public Result<Page<AssetFile>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "24") long size,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String keyword) {
        return Result.ok(assetService.page(page, size, category, keyword));
    }

    @GetMapping("/{id}")
    public Result<AssetFile> get(@PathVariable Long id) {
        return Result.ok(assetService.getAsset(id));
    }

    /** 上传素材（multipart/form-data：file + 可选 name/category） */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AssetFile> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) String name,
                                    @RequestParam(required = false) String category) {
        return Result.ok(assetService.upload(file, name, category));
    }

    @PutMapping("/{id}")
    public Result<AssetFile> update(@PathVariable Long id, @RequestBody AssetUpdateDTO req) {
        return Result.ok(assetService.updateMeta(id, req.getName(), req.getCategory(), req.getStatus()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return Result.ok();
    }

    /** 读取文件内容（下载/预览），鉴权后通过登录态携带的 Token 调用 */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        AssetFile asset = assetService.getAsset(id);
        byte[] bytes = assetService.readContent(id);
        String mediaType = asset.getContentType() == null ? "application/octet-stream" : asset.getContentType();
        String inlineType = "image".equals(asset.getCategory()) ? "inline" : "attachment";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, inlineType + "; filename*=UTF-8''" + encode(asset.getName()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(bytes);
    }

    private String encode(String name) {
        return java.net.URLEncoder.encode(name == null ? "file" : name, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
