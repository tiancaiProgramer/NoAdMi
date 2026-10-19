## 实现边界

### 米家

- 开屏和个人中心广告：仅针对 `_m_j.uf1.OoooOOO(Context,String,String,boolean)` 的两个专用文件名 `switch_manger_open_screen_cache_data`、`switch_manger_banner_cache_data` 返回 false；其他设置不改。`SettingMainPageV2.Oooo000(View,ArrayList,boolean)` 同时使用空列表分支收起横幅容器。
- 底栏：在 `TabPageIndicatorNew.notifyDataSetChanged()` 之后，读取原生 TabView 的 TabId，将选择隐藏的按钮设为 GONE。保留原导航注册表、页面数量和 Fragment 索引，不重新编号，不手动通知页面适配器。选中项被隐藏时使用原生索引切换到保留项；全部隐藏时保留原底栏。商城兼容 ShopYouPin / ShopGlobal 两种标识，未知标签原样保留。
- “我的”：只在 `SettingMainPageV2.onViewCreated(View,Bundle)` 的页面根视图内查找经过核对的 13 个 ItemOptionView 资源 ID。先核对完整的 13 项 ID/资源名称映射，未知映射直接跳过，避免 Play 包复用相同 ID 时误隐藏。选择隐藏的行设为 GONE；在页面布局更新后重新应用，避免异步配置恢复已隐藏行。某个原生卡片中所有菜单行不可见时收起该卡片。观察器随视图分离解除，重新附着时恢复。没有全局拦截 View.setVisibility，也不按模糊文案全局隐藏。
- 不写入米家原生开关、官方配置接口或账号设置；仅调整当前进程界面与广告展示判断。关闭规则并重新启动即停止应用这些覆盖。

### 运动健康

- `SplashAdPreference.checkShowSplashAdEnable()`、`SplashAdImpl.checkRefreshSplashInfo(boolean)` 返回 false，跳过 `refreshSplashAdInfo(boolean)`。
- `HealthViewModel.queryOperationalBannerList()` 返回空列表初始值的 MutableLiveData，走原生空列表渲染分支。
- `DeviceTabV4ViewModel.fillBannerList(List,BannerRequestParamV2,boolean)` 仅对非空且全部为整数 11/12 的资源位列表清空广告列表并跳过请求。其他资源位、正常设备连接、表盘市场及设置不在该规则内。
- 两个已核对的 RN 加载器 `CatalystInstanceImpl`、`YRNCatalystInstanceImpl`：只对匹配指定页面完整结构指纹的 bundle 生成私有缓存副本。“我的”页跳过 AdBanner 和选定营销卡片的创建表达式。保留其他组件、原文件和 source URL；指纹变化原样放行。
- “运动”页仅过滤 `fetchSportTabData` 的返回列表，分别移除 `sportGroup`、`activityRecommend`、数值类型 3（横幅）。保留运动入口、记录、训练指标、路线和其他类型。请求和原始脚本保持不变，不修改共享活动详情组件。校验唯一导出、运动数据结构和目标表达式；允许动态更新重新编号 Metro 模块，结构变化时跳过。不使用应用版本号限制加载。
- “我的”功能入口：仅在唯一的 `QuickActions` 模块内按图标与翻译键核对并移除活动、课程、亲友、订单、资产的创建表达式。已知横栏所有子项均为 null 时整行收起；未知入口保留，旧内置脚本中的奖品入口也不会被误删。仅在匹配完整结构的 `CNFeatureCards` 模块内重排小习惯、周报、活力竞赛，自动收起对应列和间隔；三项全隐藏时整块返回 null。组件详情页、数据与记录保持原样，改变后的布局指纹不匹配时跳过。

本模块不全局拦截网络请求、弹窗或所有包含“广告”文字的视图。失败的规则独立记录并跳过，日志不输出账号或健康数据。


### Play 地区兼容（默认关闭）

- 新增 `com.xiaomi.wearable` 主进程作用域。versionCode 19 起也运行经结构校验的广告与入口规则；国内版 `com.mi.health` 保持其原包名与主进程边界。地区兼容仅用于 Play 包，与其他选项独立。
- 运动健康：仅当 `CountryInfo.getName()` 为 CN（大小写不敏感）时，让 `RegionSelectListActivity.shouldShowMainLandSwitchDialog(CountryInfo)` 返回 false，由宿主继续原选择流程。其他地区、切换确认和地区保存不改。
- 运动健康大陆版迁移：`RegionUtil.isInnerCN()` 仅在最近的宿主调用帧为下列精确类/方法时覆盖 true 结果：`SplashActivity$startNextActivity$1.invokeSuspend`、`PrivacyActivity.handlePrivacyAgree`、`WelcomeActivity.onCreate$lambda$3`、`InternalTestActivity.onNextStep`。这些调用在原先的欢迎、隐私和地区检查之后决定是否进入迁移引导；不自动同意隐私条款。内部服务调用会被最近调用帧检查排除，地区查询在其余路径保持原值。保留 Play 渠道标识与原有正常主页路由。
- 米家：`_m_j.wq5.OoooOO0()` 返回 false，关闭地区列表/关于页的分包下载提示。安装前校验同类下载 URL 构造器的方法签名。
- 米家专用下载检查：`com.xiaomi.smarthome.task.OooOO0.OooO0o0(FragmentActivity,Continuation)` 直接返回宿主 Unit 单例，正常完成任务，避免悬挂协程。安装前校验 `SplitePackageCheckJob$onStart$1.this$0` 的类型、方法返回类型与 Unit 实例。此任务只处理分包下载检查，不拦截其他启动任务。
- 均不使用全局渠道伪装、地区伪装、网络替换或通用弹窗拦截。不写入目标应用设置；用户主动切换地区时，宿主仍可执行其原有登录和缓存处理。关闭开关只移除覆盖，不回滚已选地区。
- 当前核对 Play 米家 11.7.704、Play 运动健康 3.59.1i 的 DEX 声明和调用路径；客户端放行与服务端可用性分开记录。

### Play 广告与入口适配（versionCode 19）

- 米家设置读取增加 `_m_j.l22.OooOooo(Context,String,String,boolean)`，仅覆盖原先两个专用广告文件；横幅列表增加 `SettingMainPageV2.Oooo0(View,ArrayList,boolean)`，仍走原生空列表分支。候选方法必须匹配返回类型及静态属性。
- 米家底栏增加 `TabPageIndicatorNew.OooO00o()`；安装前校验 `_m_j.s1e.o00O0O` 的 `_m_j.hh6` 类型、`setTabInfo` 参数及 descriptor 的 TabId 枚举字段。仍只隐藏原生 TabView，保留 Fragment 索引和全隐藏回退。
- 米家“我的”在原国内版资源映射之外增加独立的 Play 13 项 ID/资源名映射。必须完整匹配其中一套映射，之后将同一逻辑选项映射到对应资源；不能将国内版整数 ID 直接用于 Play 页面。只改该页面的 ItemOptionView，保持原生隐藏状态，收起空卡片。
- 运动健康经实机类与方法反射核对后，在两个包的主进程加载开屏、健康页、设备页及 RN 页面规则。地区兼容不作为去广告开关的前置条件，也不将 Play 包伪装成国内渠道。
- Play 内置 Mine/Sport 脚本匹配原有结构指纹；新增真实脚本隔离与幂等性测试。已核对的 Play 内置 QuickActions 包含“奖品”而没有“我的资产”，资产开关无对应目标时原样保留。
- Play 的 FOREIGN / FOREIGN_CN “我的”布局使用 `ForeignFeatureCards`。仅在唯一的 RegionPageMap 模块、三处精确创建表达式及 ForeignFeatureCards 的活力竞赛结构均匹配时，由“活力竞赛”开关移除该页面的创建表达式。共享卡片模块、详情页、数据接口和地区/渠道判断不变。
- 36 项实机探针仅校验方法、字段及资源，不执行业务方法；真实 Hook 命中与界面检查单独记录。探针与真实脚本测试位于 `tests/android`、`tests/integration`，不打包入模块 APK，也不附带目标应用源码或脚本。
