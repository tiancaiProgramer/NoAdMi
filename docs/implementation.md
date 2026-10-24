### 原生详情页推荐（0.11.7）

沿用 `market_recommend`，在应用商店主进程中增加 `DetailDirectFragment.handleResponseData(ResponseTask)` 与 `BaseViewModel.parseResponse(INativeFragmentContext,int,JSONObject)` 的组合过滤。先核对方法和返回类型，只记住请求标识恰为 `app/directScreenFeeds4Pad/{appId}` 的响应对象；解析完成后还要求上下文为原生直屏详情、页号为 0、结果全部为 `BaseNativeComponent`，才返回新的空列表。

响应通过协程和队列异步传递，因此使用弱引用按对象身份关联，不使用线程局部标记；对象不再使用后可回收。保留 JSON、缓存原列表、响应队列、评分评论和基本信息响应以及正常加载完成流程。空、未知或异常结果保留原状。网页详情和首页过滤仍独立限定各自上下文。日志只记录安装与首次移除数量。

应用商店 5.18.0（5003340）实机以首页进入“红果免费短剧”的原生详情页验证：关闭现有开关时评论下方两组推荐恢复，开启时移除 2 个组件，评分、评论入口、截图、版本、隐私权限和底部安装入口保留。外部深链会进入网页详情，另以米家网页详情验证原脚本仍移除 3 张推荐卡片。详细环境、哈希、设置恢复和验证范围见 verification.json。

### 菜单层级排版（0.11.5）

MainActivity 按 foldLevels 记录显示层级。顶层保留主卡片，第二层标题使用浅绿色背景和独立组间距；内层标题采用更浅的背景。展开容器在左侧加入细引导线和缩进，子开关仍保留原直接父容器，计数与总开关继续按实际后代控件收集。展开容器与正文同时切换可见性，避免收起后残留引导线和空白。测试版本说明应用相同的二级标题样式。

该版本只更改显示层级与测试选择器，19/29/11 分类、规则偏好键、默认值和所有广告实现保持不变。本地构建及759项当前模块实机检查通过；实际测量引导线宽高与内层标题缩进，并核对58条远端设置、29条UI设置、三个本地偏好文件和7个作用域完整恢复。

### 分类与统一广告总开关（0.11.4，当前）

主页三组为广告管理、程序管理、关于。广告管理中先显示独立的官方广告折叠，再显示默认关闭的 App 广告折叠；19 个广告规则位于六个应用标题下，程序管理仅收纳另外29个功能规则。没有新增规则键或设置迁移，地区兼容与运动团等单选项直接呈现。两个充值入口按需求加入显式广告allowlist，默认值和保存值保持不变。

App广告总开关从这19个已注册RuleToggle收集可用成员，使用一次事务保存；SettingCounts统一计算全部/部分/关闭和不可用状态。程序组折叠的计数来自各自实际控件；单项和折叠使用52dp最小行高、22dp箭头预留区。官方与普通广告开关共用SettingsRow样式。

官方一键关闭仍走原有异步读取、逐项关闭、回读确认流程。OfficialAdCounts仅把已知false计为关闭；未知不计成功。完成开关是单向操作，全部11项确认关闭才勾选并禁用，忙碌时不呈现完成；不会批量开启官方广告。UI自动刷新和填值时屏蔽监听。非布尔或缺失enabled字段同样不计作关闭成功。

实机回归通过模块UI修改广告规则，finally完整恢复规则及折叠快照。官方全部/部分/未知状态在脱离窗口的面板中模拟验证，不写真实官方设置；另有只读真实状态加载测试。截图工具通过SharedPreferences同步commit还原安装前折叠快照，主机再只读比较远端配置、三个本地偏好文件和作用域。执行结果以本版verification.json为准。下方按版本保留历史实现与验证范围。


### 联动清单与统一计数（0.11.3，历史）

`MainActivity.bulkMembers` 从本组注册的子开关中按原 `BulkEnablePolicy` 收集成员，联动清单、计数和批量写入共用该集合。清单使用子开关自身标题，避免复制维护；未安装项目单列且不写入。原允许列表、偏好键、默认值、保存事务和全部功能隔离保持不变，无新增设置迁移。

分组总开关与内层分类共用 `SettingCounts`，可用项才进入分子/分母，不可用项单独计数。国内/Play运动健康共用规则不重复计数；只有Play兼容子项要求Play包已安装。总开关显示全部关闭、部分开启、全部开启或无可用项目，部分开启时视觉摘要和无障碍状态均明确提示；点击仍补全本组剩余广告隐藏。内层分类标注“已启用设置”，官方面板标注独立的11项配置。计数只说明配置状态，不表示已实际命中目标广告。

单元测试覆盖五组规模的全部开启组合、不可用项对分子/分母/总状态的隔离及国内版运动健康的3可用/1不可用案例。实机测试脚本另校验联动项目与实际受控开关逐项一致、总数不变和全部/部分/关闭的显示；是否执行以本版verification.json为准。

Windows构建显式按UTF-8读取测试源码，并绕过d8.bat的cmd.exe命令长度限制，使用同一SDK附带D8的Java入口。应用Hook及其历史实机测试批次保持原记录，没有将本地构建测试当作新一轮应用实机验证。

### 分组广告总开关（0.11.2）

每个应用／系统大折叠内提供“一键关闭广告”Switch。从当前组的已注册规则控件收集目标，要求应用已安装且命中显式广告类 `BulkEnablePolicy`；按开启／关闭一次事务保存广告规则，备份后同步 Switch 和折叠计数。总开关从当前子项推导“全部开启”，部分开启显示数量，不另存一个与子项冲突的总开关键。刷新期间屏蔽监听，避免递归写入和多次通知。

功能入口、全部米家底栏、运动团、流量／话费充值服务、地区实验、关于、官方广告状态及未知键都不纳入。系统官方开关保留异步关闭／回读流程。实机批量测试先设广告项关闭，验证开启、关闭、部分开启后的总开关状态及跨组／功能入口隔离，结束后恢复测试前所有模块偏好。

### 应用商店统一推荐（0.11.2）

`market_recommend` 统一控制推荐决策、首页横幅和详情推荐脚本，`market_splash` 仍独立。`PromotionSettings` 在新键未写入时取旧六项逻辑或，界面连接框架后只迁移一次；新键即使为 false 也优先，不回写旧键。256 个组合断言覆盖旧值、新键缺失与显式开关优先。

详情脚本同时识别 `.rec-apps.card` 与 `.applist.card`，要求标题归属于当前卡片。组件须直接绑定该根节点，名称为 `recApps` 或 `AppListHorizon`，keyName 为 `detailRelatedApps`、`appDetailRecApps`、`detailSameCategoryApps`、`detailAlsoFavorite`（允许数字实例后缀），才统一收起同类推荐。没有组件绑定的旧资源保留完整标题／用户还喜欢结尾的有限匹配，不按所有页面里的“热门”等泛词隐藏。MutationObserver 按帧合并异步变化，保留原 CSS display 和优先级，复用为普通内容时恢复。不读取或写入账号信息、Cookies 或存储。

已重新核对 HyperCeiler 当前 main 的 DisableAds（2026-10-23，5af3daeff128130afe10c1aa1d1534d425ec2f71）。上游包含 showRecommend 等方法，本机版本并不存在对应方法；没有直接移植它的 equals、兼容性、评论或儿童限制修改。以当前应用的广告条件和组件实际调用实现。

### 应用商店首页与详情推荐（0.11.1 历史实现，详情筛选已由上述统一规则替换）

`MarketPageHooks` 仅在应用商店主进程加载。首页在 `BaseViewModel.parseResponse` 完成后要求上下文属于 `HomeFeatureTabFragment`、页号 0、同类型组件列表，且首项类型为已核对的大幅轮播；复制列表移除首项，原列表不修改。矩阵、普通横排应用、游戏页、其他页码与未知组件保留。

详情页 Hook 商店自己的 `CommonWebView$3.onPageFinished`，还需命中 `inner_webview` 与详情 HTML 路径，才注入本项目自己的脚本。脚本只遍历 `.rec-apps.card`，要求对应推荐标题节点归属当前卡片，完整匹配已核对标题后设置整块 display:none。MutationObserver 对异步插入／文本变化按动画帧合并处理，不监听自身样式属性，避免循环。保存原 display 与优先级，卡片变为普通内容或被复用时恢复；重复加载不重复注册观察者。

HTML 和 DOM 结构依据手机实际加载的 web-res-3176 核对，APK 内旧资源 2769 作为历史线索，均只留在私有工作目录。脚本从 `src/main/assets/market-detail.js` 由 `tools/embed_market_detail.py` 在构建时自动嵌入 Java，确保目标应用不通过自己的 Context 读取模块资产。日志仅包含两类隐藏数量，不包含 URL 查询、应用列表、页面 HTML 或账号数据。临时结构诊断已移除。

### 浏览器与应用商店广告 Hook（0.11.0）

`PromotionRules` 只列出目标应用的原生广告条件方法；`PromotionHooks` 在主进程安装，并逐个核对参数及返回类型。开关默认关闭，不修改官方配置。浏览器抑制主动开屏请求和被动开屏条件；应用商店利用原生“不展示开屏”分支保留后续回调，不直接中断整个 Activity。原生推荐只返回不支持，不干预安装、兼容性、账号或儿童限制。

信息流在浏览器自己的 `ResultResponse.getData` 返回后检查：仅处理全为 `BrowserFeedModel`（或 null）的列表，读取原生 AdInfo 中非空广告 tag／template 标记，删除命中的广告项。`AdFeedFilter` 不修改原列表，未命中时保持原对象；未知列表、单个对象、异常与普通内容保持原样。只有过滤结果变化时才记录一次不含内容的命中日志。不监听全局网络、不扫描网页 DOM，也不拦截 WebView 的普通加载。

已核对本机 18 个精确 DEX 方法契约，其中 14 个广告决策拦截点、1 个返回列表拦截点与 3 个只读广告标记访问器。方法不匹配时跳过该条并保留原行为，不以版本号禁止其他规则运行。上游参考源码保留在私有工作目录，不随本项目打包。

## 实现边界

### 官方设置加载提示（0.9.6）

- 状态行置于官方面板最上方，展开触发刷新时同步显示等待提示和 indeterminate ProgressBar；后台读取按应用组更新完成数量与当前项名称。
- 保存和批量操作共用 busy 进度提示；结束后隐藏进度圈，恢复可用操作。状态文本使用 polite accessibility live region，读取失败保留逐项错误。

### 紧凑纵向间距（0.9.5）

- UiSpacing 对既有 dp 纵向间距统一乘以 0.75，再按当前 density 换算像素；不使用物理 PPI 或固定像素行高。主设置与官方开关面板共用该比例。
- 页面顶部安全留白、横向尺寸与 sp 字号保留，内容仍用 wrap_content 自适应多行；分类、开关和更新行 minimumHeight 为 48dp，字体变大时可自然增高。

### 关于分组（0.9.4）

- “关于”作为页面末尾的独立折叠分组，包含检查更新、显示桌面图标和二级测试版本折叠。新增 UI 状态键 `about` 默认 false，测试版本继续使用 `tested_versions`，不迁移或覆盖原有设置。
- 更新链接、桌面启动别名与测试版本排序逻辑不变；嵌套折叠的可见性受“关于”父容器控制，父容器收起不重置子折叠状态。

### 小米账号自身进程后台控制（0.9.3）

- `com.xiaomi.account` 主进程新增作用域，在其 Application.onCreate 完成后注册控制接收器。目标进程使用原有真实 Context、身份与官方初始化，不修改操作包名、UID、账号凭证或安全校验。
- 控制接收器要求模块拥有的 signature 级权限；Android 14+ 通过广播共享身份取得系统提供的发送者 UID/包名，核对必须是本机 NoAdMi。仅接受带 32 位随机请求标识的 ping/write，写值必须为 Boolean；响应只含广告状态与成功标志。
- 固定核对账号 `SystemAdFragment$c` 后台方法、构造器与 `t4.r` 结果字段。在线程池调用原应用后台 worker，保留儿童账号检查、账号登录、服务请求、原生重试及成功后的状态缓存更新。官方结果码为 0、返回值匹配且状态回读一致才显示成功；不调用需要 Activity 的 post-execute 回调，不导出 token 或账号标识。
- 客户端只在未连接时通过 Root 启动固定的原生认证后台服务，随后重新探测桥接；不会为了后台操作启动 Activity，不重试可能已提交的写请求。
- 未勾选作用域、Android 14 以下、结构变化或操作失败时，系统工具广告的 Switch 隐藏，显示唯一官方设置入口。用户主动点击时才由 Root 启动固定 SystemAdActivity；失败项不自动跳转。其他十项保持后台操作。

### 无跳转的后台开关（0.9.2）

- 删除通用 SystemShortcuts 与全部旧页面入口。0.9.3 仅为账号后台不可用的情况保留手动页面入口，没有 UIAutomator 或模拟点击实现。
- 其他十项通过原有配置存储后台修改；账号操作不能从通用 Root worker 写入，只能通过上述受保护的真实账号进程接口完成。

### 官方广告直接控制与保存提示（0.9.1）

- `NativeAdRules` 固定定义 11 个广告设置，不接受外部包名、路径、配置键或任意命令。Root 辅助进程使用 `su -M` 进入全局挂载视图，避免继承模块应用自己的目录隔离视图；不改变系统全局隔离或 SELinux 配置。只支持机主用户空间，辅助进程结束后退出。
- 目标配置先核对原生 XML 中对应偏好项，检查应用目录权限；失败时不把默认值当成真实状态。保存前停止目标应用，随后切换至目标 UID，通过 SharedPreferences 提交指定布尔键并回读；其他设置保留。天气遵循应用的 FBE 封装，使用设备保护存储中的 `com.miui.weather2.information`；其他普通偏好沿用各应用自身文件。
- 浏览器使用目标包自身 MMKV 实现打开 `kv_prefs`（多进程模式 4），只写两个指定广告/内容推荐键。下载管理“资源推荐”按官方行为同时设置 `info_flow_show` 与 `Banner_show`。
- 系统个性化广告使用 Global `personalized_ad_enabled` 的最低位，保留其他位；状态变化后记录原生时间键。小米账号广告的缓存键为 `passport_ad_status`，后台写入及回读详见上述 0.9.3 接口。
- 一键关闭先重新读取，跳过已关闭项目；各项独立反馈失败，界面按钮在操作期间禁用。文件管理扩展服务包含云盘操作，排除批量控制。0.9.2 已移除原生快捷入口。
- `SaveNoticeGate` 使用单调时钟，仅限制成功 Toast：距上次成功提示不足 3000 毫秒时不重复提示。每次偏好写入立即执行，失败提示不受限制。

### 系统应用广告（0.9.0）

- `com.miui.securitycenter` 与 `com.miui.securityadd` 仅主进程加入目标集合，设置、天气、系统框架不加入 Hook 作用域。
- 网络助手仅在 `NetworkAssistantActivity.onCreate(Bundle)` 后观察该 Activity 的视图。匹配三个明确 Toolbar/Banner 控件类和 `id/name`，再精确匹配四个已核对的标题。选中的行设为 GONE；不删除列表元素、不重排数据、不改变网络策略或套餐。观察器随窗口分离解绑；控件被复用为其他标题时恢复本规则记录的可见状态。
- 游戏盒子的 `GameBoosterRichWebActivity$13.getConfiguration()` 先核对 `IPageProvider` 及字符串返回值，原 JSON 必须具有 `is_international` 布尔值和 language/device/gpu_enable。仅覆盖该网页使用的布局参数，保留其他字段，不全局修改 Build.IS_INTERNATIONAL_BUILD。当前公开主页用此参数决定热玩排行、推荐列表和大卡片是否渲染。
- 空游戏列表推广在专属 WebViewClient.onPageFinished 后注入观察器。仅对 HTTPS 主机 gamespace.sec.miui.com 的根路径生效；同时要求 `.text-bg` 下唯一图片和兄弟 `.get-width` / `.mask` 结构，才隐藏其所属空状态推广组件。不改 localStorage、不拦截网络、不对其他网页或全部 WebView 执行模糊文案匹配。
- 迅游新人推广仅令 `XunYouGuideDialogUtils.isShowNewUserDialog()` 返回 false，不吞掉通用隐私对话框或手动购买流程。
- 0.9.0 的官方页面快捷入口已于 0.9.2 删除，当前改用上述后台控制表。
- 参考公开 HyperOS 去广告教程确定检查范围，所有 Hook 依据本机 APK 和游戏盒子官方网页独立核对。未复制第三方模块实现。

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

### 现代框架与设置

- 入口为 `HookEntry extends XposedModule`，通过 `META-INF/xposed/java_init.list`、`module.prop`、`scope.list` 注册。主进程与首包校验保持不变；现代框架不再通过自身 Hook 判断激活。
- `HookRuntime` 将各页面规则的前后回调接入 API 102 Chain，修改参数使用 `proceed(args)`；提前返回不调用宿主；宿主异常保持原对象，后置回调失败不二次调用宿主。反射辅助类为本模块代码，不包装或调用旧 Xposed API。
- 模块应用接收官方 XposedProvider 服务，使用 `XposedService.getRemotePreferences` 写设置；目标使用 `XposedModule.getRemotePreferences` 只读。远程设置首次导入完成标志存在之前，目标不加载任何规则。
- 私有本地镜像用于备份；现有远程设置优先，明确准备的旧设置副本可覆盖旧键而不删除其他现代键。布尔 false 与缺失键区分，迁移不通过默认值判断用户选择。
- 旧目录由用户明确运行迁移工具读取，无全局 Root 权限申请，无权限放宽。私有导入 XML 限长并只接受布尔设置，提交成功后删除副本。
- 更新入口是外部浏览器 ACTION_VIEW，目标固定为本仓库 Releases；包含预览版本，不使用可能遗漏预览版的 `/latest` 跳转。
