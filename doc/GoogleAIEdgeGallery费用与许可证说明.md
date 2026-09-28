# Google AI Edge Gallery 费用与许可证说明

核对日期：2026-09-28

## 结论

Google 官方的 Google AI Edge Gallery 当前是免费、开源的应用，没有“免费试用 7 天后自动收费”的订阅机制。

- 官方 Android 包名：`com.google.aiedge.gallery`
- 官方 GitHub：https://github.com/google-ai-edge/gallery
- 官方 Google Play：https://play.google.com/store/apps/details?id=com.google.aiedge.gallery
- 项目源码许可证：Apache License 2.0
- 当前源码没有 Google Play Billing、订阅、购买或试用期实现。
- 模型在手机本地推理，正常使用没有按 token 收费。

## 容易混淆的第三方 App

Google Play 上存在名称近似的第三方应用，例如包名 `com.ai.edge.gallery` 的 `AI Gallery - Offline AI Chat`。它不是 Google AI Edge Gallery，商店页面标注了广告和应用内购买。

如果界面出现“7 天免费试用”、订阅价格或自动续费，应先检查应用详情页中的：

1. 开发者是否为 Google LLC / Research at Google。
2. 包名是否为 `com.google.aiedge.gallery`。
3. 商店地址是否包含 `id=com.google.aiedge.gallery`。

## 免费不等于没有其他成本或限制

- 首次下载模型会消耗网络流量和数 GB 存储空间。
- 本地推理会消耗电量、内存、CPU/GPU 资源。
- 某些 Hugging Face 模型可能要求登录、提供访问令牌或接受模型许可证，但这不等于订阅收费。
- 每个模型有自己的许可证和使用条款；二次开发和商业分发时应分别核对。
- Skills 中的 Wikipedia、Google Maps、CDN 等联网能力可能受网络环境或第三方服务条款影响。
- 如果二次开发时接入收费云端 API、商业语音服务或第三方订阅 SDK，新增部分可能产生费用，但不属于官方 Gallery 本身的收费。

## 本项目源码核对

`Android/src/app/build.gradle.kts` 中的官方应用 ID 为：

```kotlin
applicationId = "com.google.aiedge.gallery"
```

项目根目录的 `LICENSE` 为 Apache License 2.0。源码依赖中没有发现 Google Play Billing Client，也没有订阅、购买或 7 天试用相关业务代码。

## Gemma 4 E2B 商业化说明（2026-09-28）

当前 Gallery 模型列表使用的是：

```text
litert-community/gemma-4-E2B-it-litert-lm
```

Google 官方 Gemma 4 模型卡和上述 LiteRT-LM 转换模型页面都将许可证标为 Apache License 2.0。Apache 2.0 授予免费的、免版税的版权和相关专利许可，因此：

- 可以在收费 App、企业 App 和商业服务中使用 Gemma 4 E2B。
- 可以在手机本地运行，不按 token 向 Google 支付费用。
- 可以修改、微调和分发模型或衍生版本。
- 可以对自己的 App、服务、支持或定制功能收费。

商业化时仍需遵守 Apache 2.0 的主要要求：

1. 向模型接收者提供一份 Apache License 2.0。
2. 保留适用的版权、专利、商标和归属声明。
3. 修改模型或相关文件时，对修改过的文件作出明显说明。
4. 如果上游分发包包含 `NOTICE`，继续提供其中适用的声明。
5. 不得使用 Google、Gemma 的商标暗示产品获得 Google 官方背书。

推荐在 App 中增加“开源许可/第三方声明”页面，并随安装包提供：

```text
licenses/Apache-2.0.txt
notices/Gemma-4-E2B-NOTICE.txt
```

声明中至少写明使用的准确模型名称、模型来源链接、许可证链接，以及是否做过量化、转换或微调。

Google 的旧版 `Gemma Terms of Use` 页面明确提示：Gemma 4 应查看单独的 Gemma 4 License。当前该链接指向 Apache License 2.0，因此不要把旧版 Gemma 的自定义再分发条款误套到 Gemma 4 上。

不属于模型授权费、但商业项目仍可能发生的成本包括应用商店费用、模型托管/CDN 流量、服务器算力、测试设备、技术支持，以及接入的第三方云 API。模型生成内容也不是自动无侵权风险，产品方仍需负责隐私、内容安全、消费者保护和行业监管合规。

官方参考：

- Gemma 4 模型卡：https://ai.google.dev/gemma/docs/core/model_card_4
- Gemma 4 Apache 2.0 License：https://ai.google.dev/gemma/apache_2
- Google 原始模型：https://huggingface.co/google/gemma-4-E2B-it
- Gallery 使用的 LiteRT-LM 模型：https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
