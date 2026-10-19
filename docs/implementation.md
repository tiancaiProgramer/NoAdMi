## 实现边界

### 米家

- 开屏和个人中心广告：仅针对 `_m_j.uf1.OoooOOO(Context,String,String,boolean)` 的两个专用文件名 `switch_manger_open_screen_cache_data`、`switch_manger_banner_cache_data` 返回 false；其他设置不改。`SettingMainPageV2.Oooo000(View,ArrayList,boolean)` 同时使用空列表分支收起横幅容器。
- 底栏：在 `TabPageIndicatorNew.notifyDataSetChanged()` 之后，读取原生 TabView 的 TabId，将选择隐藏的按钮设为 GONE。保留原导航注册表、页面数量和 Fragment 索引，不重新编号，不手动通知页面适配器。选中项被隐藏时使用原生索引切换到保留项；全部隐藏时保留原底栏。商城兼容 ShopYouPin / ShopGlobal 两种标识，未知标签原样保留。
- “我的”：只在 `SettingMainPageV2.onViewCreated(View,Bundle)` 的页面根视图内查找经过核对的 13 个 ItemOptionView 资源 ID。选择隐藏的行设为 GONE；在页面布局更新后重新应用，避免异步配置恢复已隐藏行。某个原生卡片中所有菜单行不可见时收起该卡片。观察器随视图分离解除，重新附着时恢复。没有全局拦截 View.setVisibility，也不按模糊文案全局隐藏。
- 不写入米家原生开关、官方配置接口或账号设置；仅调整当前进程界面与广告展示判断。关闭规则并重新启动即停止应用这些覆盖。

### 运动健康

- `SplashAdPreference.checkShowSplashAdEnable()`、`SplashAdImpl.checkRefreshSplashInfo(boolean)` 返回 false，跳过 `refreshSplashAdInfo(boolean)`。
- `HealthViewModel.queryOperationalBannerList()` 返回空列表初始值的 MutableLiveData，走原生空列表渲染分支。
- `DeviceTabV4ViewModel.fillBannerList(List,BannerRequestParamV2,boolean)` 仅对非空且全部为整数 11/12 的资源位列表清空广告列表并跳过请求。其他资源位、正常设备连接、表盘市场及设置不在该规则内。
- 两个已核对的 RN 加载器 `CatalystInstanceImpl`、`YRNCatalystInstanceImpl`：只对匹配指定页面完整结构指纹的 bundle 生成私有缓存副本。“我的”页跳过 AdBanner 和选定营销卡片的创建表达式。保留其他组件、原文件和 source URL；指纹变化原样放行。
- “运动”页仅过滤 `fetchSportTabData` 的返回列表，分别移除 `sportGroup`、`activityRecommend`、数值类型 3（横幅）。保留运动入口、记录、训练指标、路线和其他类型。请求和原始脚本保持不变，不修改共享活动详情组件。校验应用版本、唯一导出、运动数据结构和目标表达式；允许动态更新重新编号 Metro 模块，结构变化时跳过。
- “我的”功能入口：仅在唯一的 `QuickActions` 模块内按图标与翻译键核对并移除活动、课程、亲友、订单、资产的创建表达式。已知横栏所有子项均为 null 时整行收起；未知入口保留，旧内置脚本中的奖品入口也不会被误删。仅在匹配完整结构的 `CNFeatureCards` 模块内重排小习惯、周报、活力竞赛，自动收起对应列和间隔；三项全隐藏时整块返回 null。组件详情页、数据与记录保持原样，改变后的布局指纹不匹配时跳过。

本模块不全局拦截网络请求、弹窗或所有包含“广告”文字的视图。失败的规则独立记录并跳过，日志不输出账号或健康数据。

