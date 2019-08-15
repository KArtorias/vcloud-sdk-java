## TTVcloud SDK for Java

### - Maven依赖
```
<dependency>
    <groupId>com.bytedanceapi</groupId>
    <artifactId>vcloud-sdk-java</artifactId>
    <version>0.0.5</version>
</dependency>
```

### AK/SK设置
- 在代码里显示调用VodService的方法setAccessKey/setSecretKey

- 在当前环境变量中分别设置 VCLOUD_ACCESSKEY="your ak"  VCLOUD_SECRETKEY = "your sk"

- json格式放在～/.vcloud/config中，格式为：{"ak":"your ak","sk":"your sk"}

以上优先级依次降低，建议在代码里显示设置，以便问题排查

### API

#### 上传

- 通过指定url地址上传

[uploadMediaByUrl](https://open.bytedance.com/docs/4/4652/)

- 服务端直接上传


上传视频包括 [applyUpload](https://open.bytedance.com/docs/4/2915/) 和 [commitUpload](https://open.bytedance.com/docs/4/2916/) 两步

上传封面图包括 [applyUpload](https://open.bytedance.com/docs/4/2915/) 和 [modifyVideoInfo](https://open.bytedance.com/docs/4/4367/) 两步


为方便用户使用，封装方法 uploadVideo 和 uploadPoster， 一步上传


#### 转码
[startTranscode](https://open.bytedance.com/docs/4/1670/)


#### 发布
[setVideoPublishStatus](https://open.bytedance.com/docs/4/4709/)


#### 播放
[getPlayInfo](https://open.bytedance.com/docs/4/2918/)

[getOriginVideoPlayInfo](https://open.bytedance.com/docs/4/11148/)

[getRedirectPlay](https://open.bytedance.com/docs/4/9205/)

#### 封面图
[getPosterUrl](https://open.bytedance.com/docs/4/5335/)

#### token相关
[getUploadAuthToken](https://open.bytedance.com/docs/4/6275/)

[getPlayAuthToken](https://open.bytedance.com/docs/4/6275/)

PS: 上述两个接口和 [getRedirectPlay](https://open.bytedance.com/docs/4/9205/) 接口中均含有 X-Amz-Expires 这个参数

关于这个参数的解释为：设置返回的playAuthToken或uploadToken或follow 302地址的有效期，目前服务端默认该参数为15min（900s），如果用户认为该有效期过长，可以传递该参数来控制过期时间
。


#### 更多示例参见
src/test/java/com/bytedanceapi/example