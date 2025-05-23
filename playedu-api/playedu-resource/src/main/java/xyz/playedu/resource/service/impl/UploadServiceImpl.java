/*
 * Copyright (C) 2023 杭州白书科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.playedu.resource.service.impl;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import xyz.playedu.common.constant.BackendConstant;
import xyz.playedu.common.constant.CommonConstant;
import xyz.playedu.common.exception.ServiceException;
import xyz.playedu.common.service.AppConfigService;
import xyz.playedu.common.types.UploadFileInfo;
import xyz.playedu.common.types.config.S3Config;
import xyz.playedu.common.util.Base64Util;
import xyz.playedu.common.util.HelperUtil;
import xyz.playedu.common.util.S3Util;
import xyz.playedu.common.util.StringUtil;
import xyz.playedu.resource.domain.Resource;
import xyz.playedu.resource.service.ResourceService;
import xyz.playedu.resource.service.UploadService;

@Service
@Slf4j
public class UploadServiceImpl implements UploadService {

    @Autowired private ResourceService resourceService;

    @Autowired private AppConfigService appConfigService;

    @Override
    @SneakyThrows
    public UploadFileInfo upload(S3Config s3Config, MultipartFile file, String dir) {
        if (file == null || file.isEmpty() || StringUtil.isEmpty(file.getOriginalFilename())) {
            throw new ServiceException("请上传文件");
        }

        // 上传上来的文件名名
        String filename = file.getOriginalFilename();

        UploadFileInfo fileInfo = new UploadFileInfo();
        // 文件大小
        fileInfo.setSize(file.getSize());
        // 解析扩展名
        fileInfo.setExtension(HelperUtil.fileExt(filename).toLowerCase());
        // 解析扩展名称对应的系统资源类型
        String type = BackendConstant.RESOURCE_EXT_2_TYPE.get(fileInfo.getExtension());
        // 附件模块上传文件 非系统格式统一为OTHER
        if (StringUtil.isEmpty(type)) {
            type = BackendConstant.RESOURCE_TYPE_OTHER;
        }
        fileInfo.setResourceType(type);
        // 检测是否为系统不支持的资源类型
        if (StringUtil.isEmpty(fileInfo.getResourceType())) {
            throw new ServiceException("当前格式不支持");
        }

        // 上传原文件的文件名
        fileInfo.setOriginalName(filename.replaceAll("(?i)." + fileInfo.getExtension(), ""));
        // 自定义新的存储文件名
        fileInfo.setSaveName(HelperUtil.randomString(32) + "." + fileInfo.getExtension());
        // 生成保存的相对路径
        if (StringUtil.isEmpty(dir)) {
            dir = BackendConstant.RESOURCE_TYPE_2_DIR.get(fileInfo.getResourceType());
        }
        fileInfo.setSavePath(dir + fileInfo.getSaveName());
        // 保存文件
        new S3Util(s3Config)
                .saveFile(
                        file,
                        fileInfo.getSavePath(),
                        BackendConstant.RESOURCE_EXT_2_CONTENT_TYPE.get(fileInfo.getExtension()));
        fileInfo.setDisk("");
        return fileInfo;
    }

    @Override
    @SneakyThrows
    public Resource storeBase64Image(
            S3Config s3Config, Integer adminId, String content, String categoryIds) {
        // data:image/jpeg;base64,
        String[] base64Rows = content.split(",");
        // 解析出content-type
        String contentType =
                base64Rows[0].replaceAll("data:", "").replaceAll(";base64", "").toLowerCase();
        // 解析出文件格式
        String ext = contentType.replaceAll("image/", "");
        // 通过文件格式解析资源类型
        String type = BackendConstant.RESOURCE_EXT_2_TYPE.get(ext);
        // 资源类型必须存在
        if (StringUtil.isEmpty(type)) {
            throw new ServiceException("当前格式不支持");
        }
        byte[] binary = Base64Util.decode(base64Rows[1]);

        String filename = HelperUtil.randomString(32) + "." + ext;
        String savePath = BackendConstant.RESOURCE_TYPE_2_DIR.get(type) + filename;

        // 保存文件
        new S3Util(s3Config)
                .saveBytes(binary, savePath, BackendConstant.RESOURCE_EXT_2_CONTENT_TYPE.get(ext));

        // 上传记录
        return resourceService.create(
                adminId,
                categoryIds,
                type,
                filename,
                ext,
                (long) binary.length,
                "",
                savePath,
                CommonConstant.ZERO,
                CommonConstant.ONE);
    }
}
