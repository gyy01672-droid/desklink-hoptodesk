# DeskLink · HopToDesk 个人分支

这个分支为安卓主控提供中文连接入口。Windows 主控与被控共用 HopToDesk 官方 x64 程序。两端使用 HopToDesk 的设备 ID、认证和连接服务。DeskLink 3.x 的邀请码和配对记录属于另一套协议，首次连接需要在 HopToDesk 上重新授权。

## Windows 控制 Windows

1. 在两台电脑上打开同一个 Windows x64 程序。在家里的电脑上查看「您的 ID」。需要人不在家也能连接时，在家里的电脑启用「无人值守访问」并设置永久密码。需要重启后或登录前接入时，在该电脑上点击「安装」以启用 Windows 服务。
2. 在外面的电脑输入家里的 ID，点击「连接」，输入刚设置的密码。勾选「记住密码」后，可从最近设备再次连接。
3. 第一次用手机热点验收时，使用默认网络设置。会话建立后把鼠标停在工具栏的绿色锁上，查看本次使用「直接连接」还是「中继连接」。

HopToDesk 的基础连接使用运营方提供的信令和中继。需要优先尝试直连时，在两端的「设置 → 网络」打开「允许直接连接」。直连失败后会尝试中继，连接仍取决于两端能否访问运营方服务。上游也提供面向受限网络的「网络兼容模式」。[官方连接说明](https://help.hoptodesk.com/)

1.46.88 的桌面直连通过 TCP 建立。要核对游戏表现，可以在实际会话内打开「显示质量监测」，同时记录所走线路、帧率和时延。[直连实现](https://gitlab.com/hoptodesk/hoptodesk/-/blob/cc56380b38c962770ed9e0d704235c94f1881248/src/client.rs#L866-867)

## 安卓控制 Windows

安装本分支的单个 APK。在首页填写家里电脑的 HopToDesk 数字设备号，点击「连接电脑」。勾选保存后，首页会出现「连接这台电脑」。首页保存的是设备号；会话首次连接时的密码验证和「记住密码」由 HopToDesk 处理。通过「打开 HopToDesk」可访问完整的会话设置。本分支可以和官方安卓客户端或 DeskLink 3.x 并存。[安卓入口源码与构建方法](android-controller/README.md)

若需要从某一端排查网络，可执行 `python tools/check_network.py`。这个工具向官方文档列出的入口做 DNS 解析和 TCP 握手。要验证两台设备之间的实际线路，还需在客户端建立会话并查看连接状态。

## 版本与来源

- Windows 使用官方签名的 HopToDesk 1.46.88 x64。SHA-256 为 `1586f3bc3538a9e7a40c5273bdfe73f50070cffae119ecc725d7e604101fff88`。[官方下载](https://www.hoptodesk.com/download)
- 客户端上游源码固定为 `cc56380b38c962770ed9e0d704235c94f1881248`。克隆本仓库后执行 `git submodule update --init` 可取得此版本。[上游源码](https://gitlab.com/hoptodesk/hoptodesk/-/tree/cc56380b38c962770ed9e0d704235c94f1881248)
- 安卓入口由本分支构建。会话内的密码验证、画面、键鼠输入和网络协议沿用上游。上游项目使用 AGPL-3.0，其品牌与版权归原权利人，公共网络由 HopToDesk 运营。[许可证](https://gitlab.com/hoptodesk/hoptodesk/-/blob/cc56380b38c962770ed9e0d704235c94f1881248/LICENSE) · [服务条款](https://www.hoptodesk.com/terms)
