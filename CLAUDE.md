<!-- generated-by: project-guardrails | 2026-09-21 | 继承根目录 CLAUDE.md，假设与缺口见 ../plan/待确认项.md -->

# 后端开发约束（`TbgBook/`）

**本文件继承根目录 `CLAUDE.md` 的全部约束**，此处只补充后端特有内容。
技术栈、接口契约、数据库、安全、协作与交付、开发原则、会话恢复流程见根目录 `CLAUDE.md`，**不在此重复**。

## 一、目录结构与模块职责表

```text
TbgBook/
├── tbgbook-admin        入口 + RuoYi 平台自带 Controller + 三份 profile 配置
├── tbgbook-framework    Security / JWT / Druid / 拦截器 / 各 @Configuration
├── tbgbook-system       sys_* 的 domain · service · mapper
├── tbgbook-common       通用工具、BaseController、AjaxResult/TableDataInfo、注解、异常基类
├── tbgbook-quartz       定时任务（qrtz_*）
├── tbgbook-generator    代码生成器（模板已 MyBatis-Plus 化）
├── tbgbook-oss          阿里云 OSS 封装
└── tbgbook-bookstore-platform   ★ 全部业务代码的落点（9 个域）
```

聚合工程，**不要为每个模块各写一套规矩**。实测依赖关系（`pom.xml`）：

```
tbgbook-common  ←──  tbgbook-system  ←──  tbgbook-framework  ←─┐
                ←──  tbgbook-generator                           │
                ←──  tbgbook-oss  ←──  tbgbook-bookstore-platform┼── tbgbook-admin
                                       ↑                         │
                                       └──  tbgbook-quartz  ─────┘
```
> `tbgbook-quartz → tbgbook-bookstore-platform` 是本项目**新增**的依赖方向（G7 已裁决，2026-09-21），用于让 8 个业务定时任务调用业务 service。落地时确认不形成依赖环。

| 模块 | 职责 | 允许依赖 | 不该放什么 |
| --- | --- | --- | --- |
| `tbgbook-common` | 通用工具（`SecurityUtils` / `RedisCache` / `FileUploadUtils` / `StringUtils`）、常量、`BaseController`、`AjaxResult` / `TableDataInfo`、`BaseEntity` / `TreeEntity`、注解（`@Log` / `@Excel` / `@DataScope` / `@RepeatSubmit` / `@RateLimiter` / `@Anonymous` / `@Sensitive`）、异常基类 | 无内部模块 | 业务逻辑；某个业务域专用的 DTO；**不要让它依赖 `tbgbook-oss`**（会形成环） |
| `tbgbook-system` | `sys_*` 的 domain / service / mapper | common | 任何 `biz_*` 业务代码 |
| `tbgbook-framework` | Security 配置、JWT、Druid 动态数据源、拦截器、`MybatisPlusConfig`、`MyMetaObjectHandler`、`GlobalExceptionHandler` | system | 业务 Service |
| `tbgbook-bookstore-platform` | **全部业务代码**：`com.tbgbook.bookstore.<域>`，9 个域 = `book` `stock` `sale` `member` `borrow` `secondhand` `ai` `message` `analysis` | framework、oss | 对 `sys_*` 的越界改动；平台侧 Controller |
| `tbgbook-oss` | `AliyunOSSProperties` / `AliyunOSSOperator` | common（仅取 lombok） | 业务逻辑 |
| `tbgbook-quartz` | 定时任务（`qrtz_*`，8 个业务任务见接口文档） | common、**`bookstore-platform`（2026-09-21 起，见下）** | 业务逻辑（业务任务调 `bookstore-platform` 的 service） |
| `tbgbook-generator` | 代码生成器与 `vm/` 模板 | common | 业务代码 |
| `tbgbook-admin` | 启动类 `RuoYiApplication`、RuoYi 平台自带 Controller（`com.tbgbook.web.controller.*`）、三份 profile | 全部 | **业务代码**（一律放 `bookstore-platform`） |

**Mapper XML 放置**：业务 mapper XML 建议放 `tbgbook-bookstore-platform/src/main/resources/mapper/<域>/`。扫描靠 `classpath*:mapper/**/*Mapper.xml` 通配，**新增模块不需要注册扫描路径**。

**G7 已裁决（2026-09-21）**：让 `tbgbook-quartz` 依赖 `tbgbook-bookstore-platform`，8 个业务定时任务写在 quartz 模块（落地时确认不形成依赖环 —— `bookstore-platform` 依赖 `framework` / `oss`，不依赖 `quartz`）。

## 二、本端技术栈

| 项 | 版本 / 方式 |
| --- | --- |
| Spring Boot | 3.5.16（3.x 线末版，**T-1：本期不动**，SB 4 迁移单独立项） |
| JDK / Maven | 17 / 3.9.x —— **`mvn` 默认跑 JDK 11，必须先 `export JAVA_HOME`**（见根 `CLAUDE.md` 第四节） |
| 持久层 | MyBatis-Plus 3.5.17（单表 CRUD + 条件构造器）+ 手写 XML（复杂查询） |
| 分页 | PageHelper 2.1.1 —— **MP 分页插件未注册，见第五节** |
| 安全 | Spring Security + JWT（`jjwt` 0.9.1，**T-10 记为技术债，本轮不动**）—— **基线锁定，不可替换** |
| 连接池 / 缓存 | Druid 1.2.28（master/slave 动态路由）/ Redis + Lettuce |
| 定时任务 | Quartz（`tbgbook-quartz`） |
| 接口文档 | springdoc 2.9.1 + `knife4j-openapi3-ui` 4.5.0（**仅 UI，不含 starter**） |
| 工具库 | Hutool（全项目通用，**优先复用，不要另写等价实现**） |
| Excel | 基线是 Apache POI（`poi` 与 `poi-ooxml` 均 5.5.1）；**已采纳** 《技术选型方案》D-4 的 FastExcel 1.3.0（`cn.idev.excel:fastexcel`）✅ 2026-09-21。✅ 2026-10-12 复核：根 pom 已同时管理 `poi` 与 `poi-ooxml` 5.5.1（版本严格一致） |
| 对象存储 | 阿里云 OSS SDK 3.18.5 |
| AI 能力 | Spring AI 1.1.8 + `spring-ai-alibaba-starter-dashscope` 1.1.2.3（chat/embedding **模型客户端**）；Qdrant v1.19（REST，自研客户端）；Redisson 3.52.0（核心包，限流与锁） |

**依赖版本集中管理**：统一在根 `pom.xml` 的 `<dependencyManagement>` 声明，**子模块不得各自锁版本**。

## 三、架构决策

**D1 — 业务代码独立成模块**（2026-09-18 落地）：全部业务代码进 `tbgbook-bookstore-platform`，按域分包。`tbgbook-admin` 只留 RuoYi 平台自带的 `sys_*` / `monitor` / `tool` Controller。**新增域时先想清楚放哪，不要顺手破坏这个边界。**

**D2 — 持久层采用 MyBatis-Plus**（C20，已拍板，不再是待确认项）：单表 CRUD 与条件构造器用 MP，复杂查询写 XML。

**D3 — 分页统一用 PageHelper**：因此 MP 的分页插件**未注册**，`selectPage` / `selectMapsPage` / `IPage` 不可用。这是有意取舍，不是遗漏。

**D4 — 接口文档是生成物**：`docs/api-documentation.md` 与 `docs/openapi.json` 由 `docs/api-gen/` 生成，改文档改规格文件。

**D5 — Knife4j 是纯前端 UI**：只依赖 `knife4j-openapi3-ui`（声明在 `tbgbook-common/pom.xml`），**不含 starter**。原因：starter 的 `Knife4jOpenApiCustomizer` 调用的 `SpringDocConfigProperties.getGroupConfigs()` 自 springdoc 2.4.0 起返回类型由 `List` 改为 `Set`，与 2.9.1 二进制不兼容；降到 2.3.0 又与 Spring Boot 3.5.16 不兼容（`ControllerAdviceBean.<init>(Object)` 已在 Spring Framework 6.2 移除），而 knife4j 版本线止于 4.5.0。
**后果**：`/doc.html` 页面与接口列表正常，但 knife4j 的**服务端增强端点不可用**（自定义文档、全局参数持久化、离线导出等页签 404）。**这是已知取舍，不要再试图换回 starter。**

**D6 — AI 供应商按需求 §6.5**（2026-09-21 拍板）：**阿里云百炼（通义千问）+ Spring AI + Spring AI Alibaba**（`spring-ai-alibaba-starter-dashscope`）。**框架只作模型客户端（chat/embedding）；RAG 编排、切片、检索、引用、灰度切换与提示词渲染由业务层自研**（2026-10-12 方案 A 修订，原「全部由框架提供」不再适用；原因：命中计数、引用溯源、重建期间灰度切换、`{{变量}}` DB 模板体系均非框架默认能力）。
✅ **2026-10-12 复核**：依赖清理（阶段一前置任务 0.1）已完成 —— 代码与配置已切到百炼（`spring.ai.dashscope`），`openai-java` / `okhttp` / Kotlin 传递链与 `<bitwalker.version>` 均已移除。

**D7 — RAG 检索链路取「方案 A（2026-10-12 版）：Java 单栈 + 百炼 + Qdrant」**（用户 2026-10-12 批准，取代 2026-09-21 的「检索在 Python 侧」版）：文档解析（PDFBox 3.0.8 + POI 5.5.1）、切片（500/50 取配置）、向量化（百炼 `text-embedding-v4`，每批 ≤10）、检索（Qdrant REST，自研客户端）全部在 `tbgbook-bookstore-platform` 的 `ai` 域内完成；**不建 `ai-node`**，**不引** `milvus-sdk-java` / Tika / Qdrant Java SDK。**T-3 / T-5 已关闭**。
⚠️ Qdrant 为部署组件（开发与生产均为容器；开发实例 `192.168.100.168:6333`，v1.19.2），Java 侧只依赖 spring-web 的 `RestClient`；`vector_id` 语义 = Qdrant point UUID。设计与实施计划见 `../docs/superpowers/specs/2026-10-12-ai-domain-design.md` 与 `../docs/superpowers/plans/2026-10-12-ai-domain.md`。

## 四、分层与职责边界

| 规则 | 违反示例 |
| --- | --- |
| **Controller 只做协议适配**：接参、校验、调 Service、组装响应。**不写业务逻辑、不直接注入 Mapper** | 在 `BizBookController` 里注入 `BizBookMapper` 直接查 | 
| **业务逻辑集中在 Service**：跨表编排、状态流转、规则计算都在 Service 层 | 前端一个页面调三个接口各取一半数据，自己在页面拼起来 |
| **数据访问只在 Mapper**：SQL / ORM 调用收敛在 Mapper，**Service 里不出现 SQL 字符串**，也不用 `@Select` 注解写查询 | `@Select("select * from biz_book where ...")` 写在 Mapper 接口上 |
| **事务边界在 Service**：`@Transactional` 标在 Service 的公开方法上，**不在 Controller 上标** | Controller 方法上标 `@Transactional` |
| **事务失效要绕开**：同类内部方法调用、非 public 方法上的 `@Transactional` 不生效；跨方法复用走注入自身或拆类 | 在同一个 Service 里 `this.doUpdate()` 调用带事务的方法，事务没生效 |
| **实体直接作为出入参是本项目的既定风格**（沿用 RuoYi，接口文档的 schema 即按表结构生成）—— 但**涉及敏感字段或成本字段时必须裁剪** | Controller 直接返回 Entity，把 `member.phone` 明文、`book.costPrice` 无权限也吐给前端 |
| **远程调用不进事务**（百炼 API / OSS，见根 `CLAUDE.md` §6.2）；且**不在同步主链路上阻塞** —— AI 不可用时必须降级，主流程照常（FR-4.7 规则 3） | 在 `@Transactional` 的结算方法里同步调百炼生成推荐语；或百炼超时导致整个结算接口挂掉 |

## 五、持久层：MyBatis-Plus

**配置现状**：`MyBatisConfig` 与 `mybatis-config.xml` **已删除**，`SqlSessionFactory` 由 MP 自动配置接管；配置块名三份 profile 统一为 **`mybatis-plus:`**（旧写法 `mybatis:` 已不存在）。

**`MybatisPlusConfig` 只注册乐观锁 `OptimisticLockerInnerInterceptor`，不注册分页插件。**

> ⚠️ **硬约束**：`selectPage` / `selectMapsPage` / `IPage` **不可用** —— 未注册分页插件时它们不会分页，会**静默返回全表且 `total` 为 0**，不报错、不告警。分页一律 `startPage()` → 查 → `getDataTable(list)`。

**审计字段自动填充**：`MyMetaObjectHandler` 负责 `createBy` / `createTime` / `updateBy` / `updateTime`（`remark` 由业务填）。
**service 里不要再手工 `setCreateTime` / `setUpdateBy`。** 无登录上下文（定时任务、初始化、匿名调用）时操作人回退为 `"system"`，不抛异常。

**逻辑删除**：`del_flag` 走 `@TableLogic`（值约定见根 `CLAUDE.md` §6.2 —— **0 = 存在 / 2 = 已删除**），配置项在 `mybatis-plus.global-config.db-config`。

**乐观锁**：`version` 列走 `@Version`，由 `MybatisPlusConfig` 注册的 `OptimisticLockerInnerInterceptor` 生效。
⚠️ **实体上没加 `@Version` 的字段不会被保护** —— 并发修改会静默覆盖。反例：`biz_stock` 有 `version` 列但实体漏了 `@Version` 注解，扣库存时两人同时改都成功。

> ⚠️ **流水类表没有 `del_flag`**（清单见根 `CLAUDE.md` §6.2）。**不要对它们套用生成器产出的基础 CRUD** —— 会退化成物理删除或写不存在的列。这几张表只增不删。

> ⚠️ **`ServiceImpl` 的包路径在 MP 3.5.9+ 已从 `extension` 拆出。** 生成器模板用的是
> `com.baomidou.mybatisplus.spring.service.impl.ServiceImpl`（artifact `mybatis-plus-spring`）。
> **老教程里的 `com.baomidou.mybatisplus.extension.service.impl.ServiceImpl` 别照着改回去。**

**`BaseEntity` 已 MP 化**：审计字段带 `@TableField(fill = ...)`，`searchValue` / `params` 带 `@TableField(exist = false)`。
**`sys_*` 存量代码除 `BaseEntity` 的注解外零改动** —— 改平台侧代码时保持这个边界（有明确需要时先问）。

**Mapper XML 只写 MP 覆盖不了的**：`resultMap` + 列表查询（+ 子表语句）。**不写** `insert` / `update` / `delete` / `selectXxxById` —— 这些由 `BaseMapper` 提供。

## 六、异常与日志

- **业务异常抛 `ServiceException`**，由 `GlobalExceptionHandler`（`tbgbook-framework/.../web/exception/`）转成统一响应，**不把堆栈直接返回前端**。反例：500 响应把 Java 堆栈吐给前端。
- **不吞异常**：`catch` 块里不能只有 `e.printStackTrace()` 或空实现后继续往下执行。
- **日志分级**：正常业务分支 `info`，可恢复问题 `warn`，真正的错误 `error` 且**带上下文参数**。反例：用户输入错误也打 `error`，真正的故障被淹没。
- **操作日志注解**：写操作加 `@Log(title = "图书管理", businessType = BusinessType.INSERT)`（`tbgbook-common` 的 `@Log`）。
- **日志不落敏感信息**（NFR-S8）：见根 `CLAUDE.md` §6.3。

## 七、代码生成器

`tbgbook-generator/src/main/resources/generator.yml`：

- `author: tbgbook`、`packageName: com.tbgbook.bookstore`（按域生成时改成 `com.tbgbook.bookstore.<域>`）、`tablePrefix: biz_`（生成类名不含 `biz_` 前缀）、`allowOverwrite: false`。

产物风格（改模板或验收生成结果按这套标准）：

| 产物 | 标准 |
| --- | --- |
| `domain` | `@Data` + `@EqualsAndHashCode(callSuper = true)` + `@TableName`；主键 `@TableId(type = IdType.AUTO)`；**无 getter/setter/toString**；`@TableLogic` / `@Version` **按列是否存在条件生成** |
| `mapper` | `extends BaseMapper<T>`，只保留 `selectXxxList` |
| `mapper.xml` | 只有 `resultMap` + `selectXxxList`（+ 子表语句），**不含** insert/update/delete/selectXxxById |
| `serviceImpl` | `extends ServiceImpl<XxxMapper, Xxx>` + `@RequiredArgsConstructor`，**无 `@Autowired` 字段注入** |
| `controller` | `@RequiredArgsConstructor` + `private final IXxxService`；`list` 用 `startPage()` + `getDataTable(list)`；`add` → `save()`，`edit` → `updateById()`，`remove` → `removeByIds(Arrays.asList(...))` |

⚠️ **生成器产出的 CRUD 对流水表不适用**（无 `del_flag`），对需要状态机的单据也不适用（需手写业务动作）。生成后必须人工裁剪。

## 八、配置与依赖

- **三份 profile**：`application-dev.yml` / `-prod.yml` / `-test.yml` 只改数据源与第三方配置；`application.yml` 仅一行 `spring.profiles.active: dev`。
  ⚠️ **三份文件目前字节完全相同** —— 环境区分**实际不存在**。**整改时点已定：阶段一前置任务 0.3**（2026-09-21），内容见根 `CLAUDE.md` §6.3 与 `../plan/待确认项.md` G4。
- **不硬编码环境地址**：数据库、Redis、AI 端点、OSS 端点全部走配置。反例：把支付回调地址写成测试环境地址。
- **数据源**：`RuoYiApplication` 排除了 `DataSourceAutoConfiguration`，数据源由 `DruidConfig` 的 master/slave 动态路由接管。加新数据源要动这里，不是加 `spring.datasource`。
- **springdoc 扫描范围**：`springdoc.group-configs` 扫 `com.tbgbook.web.controller,com.tbgbook.bookstore` —— 业务包已在范围内，**新增域不用改配置**。
- **不重复造轮子**：项目已有 Hutool、`StringUtils`、`DateUtils`、`FileUploadUtils`、`RedisCache`、`SecurityUtils`，**优先复用**。反例：项目里有 `DateUtils`，新功能又写一个 `DateUtil2`。
- **新增依赖要有理由**：说明它解决什么问题、有没有更轻的替代、许可证是否合规。
  ✅ **已落实（2026-10-12 复核）**：根 pom 的 `<dependencyManagement>` 已同时管理 `org.apache.poi:poi` 与 `poi-ooxml`（均 5.5.1，版本严格一致）—— FastExcel 传递的 `poi:5.4.1` 被中介到 5.5.1，无错配。
  ✅ **已采纳（2026-09-21）**：Excel 用 **FastExcel 1.3.0**（`cn.idev.excel:fastexcel`），落地时按上一条补齐 `poi` 管理项。

## 九、开发流程

读 `../plan/backend-plan.md` → 确认当前阶段 → 开发 → 自测（启动 + curl + 贴响应）→ 停下等用户确认 → 更新计划的「当前进度」与「进度日志」。

**后端自测的最小步骤**：

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17.0.12"
mvn clean package -Dmaven.test.skip=true
java -jar tbgbook-admin/target/tbgbook-admin.jar
# 另一个终端：登录拿 token 后调接口
curl -s http://localhost:8080/captchaImage
```

## 十、会话恢复流程

新会话开始、或执行 `/clear` 之后：

1. 读根目录 `CLAUDE.md` **与本文件**
2. 读 `../plan/backend-plan.md` 的「当前进度」与「进度日志」
3. 向用户报告：**当前阶段 / 已完成什么 / 下一步计划做什么**，并指出 `../plan/待确认项.md` 里与后端相关的未决项
4. **等用户确认后**再动手 —— 不要直接开始写代码

阶段完成后：先更新 `../plan/backend-plan.md` 的「当前进度」「阶段状态」「进度日志」，再停下等确认。
