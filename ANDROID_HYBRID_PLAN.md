# 密特堡压力监测平台 Android 混合 App 改造方案

## 目标

保留现有 Web 后台和后端接口，在 Android App 内提供更适合手机使用的核心业务入口。后台管理类功能仍在 Web 端完成，移动端重点服务现场查看、告警处理、设备/网关/模具查询和地图定位。

## 已确认现状

- 站点：`http://iot.mitebo.cn`
- HTTPS：`https://iot.mitebo.cn` 当前不可连接，Android 上线前建议修好 HTTPS
- 前端：Vue 2 + Webpack + Element UI，疑似基于 RuoYi-Vue
- 接口前缀：`/prod-api`
- 业务模块：`/yujing`
- 主要业务接口：
  - `/yujing/device/list`
  - `/yujing/device/listAll`
  - `/yujing/device/DeviceStatus`
  - `/yujing/gateway/list`
  - `/yujing/gateway/onList`
  - `/yujing/gateway/GatewayStatus`
  - `/yujing/mould/list`
  - `/yujing/mould/onList`
  - `/yujing/mould/point`
  - `/yujing/mould/notList`
  - `/yujing/mould/MouldStatus`
  - `/yujing/alarm/list`
  - `/yujing/alarm/AlarmStatus`

## 推荐架构

### Android 壳

- Kotlin 原生 Android 工程
- 内置 WebView 或 Capacitor
- 负责：
  - 登录态持久化
  - 文件上传/下载
  - 定位权限
  - 返回键
  - 网络状态提示
  - App 版本更新入口
  - 后续推送告警能力

### 移动端 Web

新增一个移动端前端入口，例如：

- `/mobile`
- `/mobile/device`
- `/mobile/alarm`
- `/mobile/gateway`
- `/mobile/mould`
- `/mobile/map`
- `/mobile/profile`

这部分仍然调用现有 `/prod-api` 接口，避免重复开发后端。

## 第一版功能范围

### 必做

- 登录页适配手机
- 首页仪表盘：设备数、在线数、告警数、模具数
- 设备列表：搜索、状态筛选、详情
- 告警列表：状态筛选、查看详情、消警/处理入口
- 网关列表：搜索、状态筛选、地图位置查看
- 模具列表：搜索、状态筛选、关联设备/点位查看
- 我的：账号信息、退出登录

### 建议第二阶段

- 告警推送
- 扫码绑定设备/网关
- 设备离线缓存
- 地图附近设备
- 图片/附件上传
- App 自动更新

## 前端改造方式

现有后台使用 Element UI，适合 PC 管理台，不适合手机。移动端建议新增一套 UI，而不是强行把现有表格压缩到手机屏幕。

建议技术路线：

- 继续 Vue 2：使用 Vant 2，改造成本最低
- 升级新移动端项目：Vue 3 + Vant 4，体验更现代，但要单独维护

如果现有源码是 RuoYi-Vue，推荐先用 Vue 2 + Vant 2，复用权限、请求封装、Token 和字典逻辑。

## Android 关键配置

- 如果暂时继续 HTTP，需要 AndroidManifest 开启明文流量
- 推荐尽快配置 HTTPS，避免 Android 版本和上架审核问题
- WebView 需要开启：
  - JavaScript
  - DOM Storage
  - 文件下载处理
  - 文件选择器
  - 定位权限回调
- 高德地图需要：
  - Android 定位权限
  - WebView 定位授权
  - 确认 JS key 的域名限制和 Android key 是否分开配置

## 里程碑

### M1：壳和登录

- Android 工程
- WebView 容器
- 登录态保持
- 返回键和网络错误页

### M2：移动端核心页面

- 首页
- 设备列表/详情
- 告警列表/详情
- 网关/模具列表

### M3：地图和文件

- 高德地图定位
- 导入导出按移动端弱化或隐藏
- 下载附件/报表

### M4：打包测试

- Debug APK
- 现场账号测试
- Android 10-15 兼容
- 弱网测试

## 需要的材料

- 前端源码，不是打包后的 `dist`
- 后端接口地址和测试账号
- Android 包名，例如 `cn.mitebo.iot`
- App 名称和图标
- 是否需要上架应用市场
- 是否需要告警推送

