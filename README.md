# 合同管理系统

基于 Spring Boot + Vue 3 的合同全流程管理系统，覆盖客户、合同、流程待办、附件、模板、版本历史、操作日志、统计、导出和权限控制等能力。项目当前保留单体中台形态，但后端已经按领域拆分服务层，便于后续演进为更清晰的模块化或分服务架构。

## 核心功能

- 用户认证：注册、登录、JWT 鉴权、当前用户注入、401 自动跳转登录。
- 权限管理：角色、权限、方法级授权、管理员保护、权限矩阵测试。
- 客户管理：客户增删改查、删除引用检查、操作日志事件记录。
- 合同流程：起草、分配、会签、定稿、审批、签订、驳回重提、撤销、删除。
- 合同信息：高级查询、时间线、流程进度、版本历史、模板起草、统计和导出。
- 附件存储：上传、下载、删除、文件魔数校验、事务提交后删除物理文件。
- 数据初始化：默认管理员、可选演示数据填充、普通启动时清理演示数据。
- 本地部署：H2 功能测试模式、PostgreSQL 持久化、Redis 缓存、可选 MySQL profile。

## 技术栈

```text
后端：Spring Boot 3, Spring Security, Spring Data JPA, Flyway, H2, PostgreSQL, Redis
前端：Vue 3, Vite, Pinia, Vue Router, Axios
测试：JUnit 5, Spring Boot Test, MockMvc, Vite build
部署：Docker Compose
```

## 目录结构

```text
backend/   Spring Boot 后端服务
frontend/  Vue 3 前端应用
docs/      项目计划、接口、数据库、前端和测试文档
scripts/   本地和 Docker 辅助脚本
```

## 后端本地启动

默认启动使用 H2 内存数据库，通过 Flyway 自动初始化表结构。

```powershell
cd backend
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

默认管理员：

```text
admin / 123456
```

## 功能测试模式

使用 `functional-test` profile 会启用 H2 内存库并填充演示数据，适合本地功能验证。

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=functional-test"
```

演示账号密码均为 `123456`：

```text
demo_drafter / 123456
demo_manager / 123456
demo_approver / 123456
```

## 演示一键启动

```powershell
.\scripts\start-demo.ps1
```

脚本会使用 `functional-test` profile 启动后端和前端，并把日志、PID 写入 `output/demo-run`。默认访问地址：

```text
前端：http://localhost:5173
后端：http://localhost:8080
```

如果端口被占用，可以指定备用端口：

```powershell
.\scripts\start-demo.ps1 -BackendPort 18080 -FrontendPort 15173
```

停止脚本：

```powershell
.\scripts\stop-demo.ps1
```

演示数据默认关闭。普通启动时系统会清理 `demo_` 用户和“演示”前缀业务数据；需要手动填充时可显式打开：

```powershell
$env:DEMO_DATA_ENABLED="true"
mvn spring-boot:run
```

## 前端本地启动

```powershell
cd frontend
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

前端默认请求本地后端接口。如需调整后端地址，可通过 Vite 环境变量配置。

## Docker 本地部署

默认 Docker 环境使用 PostgreSQL 持久化数据，并启用 Redis 缓存。

```powershell
cd backend
mvn package
cd ..
docker compose up -d --build
```

如果本机 Maven 未加入 PATH，可将上面的 `mvn` 替换为本机 Maven 绝对路径，例如：

```powershell
C:\maven\apache-maven-3.9.16\bin\mvn.cmd package
```

访问地址：

```text
前端：http://localhost:5173
后端：http://localhost:18080
Swagger：http://localhost:18080/swagger-ui/index.html
PostgreSQL：localhost:5432 / contractsys / contractsys
Redis：localhost:6379
```

查看日志：

```powershell
docker compose logs -f backend
```

停止环境：

```powershell
docker compose down
```

运行 Docker 冒烟测试：

```powershell
.\scripts\docker-smoke.ps1
```

## MySQL 联调

MySQL 配置保留为可选 profile。如果只想启动 MySQL，再用本机 Maven 跑后端：

```powershell
docker compose --profile mysql up -d mysql
```

使用 MySQL profile 启动后端：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:MYSQL_URL="jdbc:mysql://localhost:3307/contractsys?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USERNAME="contractsys"
$env:MYSQL_PASSWORD="contractsys"
mvn spring-boot:run
```

Flyway 会自动执行 `backend/src/main/resources/db/migration/mysql` 下的建表脚本。

## 常用配置

开发环境提供默认值，联调或部署时建议用环境变量覆盖：

```powershell
$env:JWT_SECRET="replace-with-at-least-32-bytes-secret"
$env:ADMIN_DEFAULT_PASSWORD="change-this-admin-password"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"
$env:SPRINGDOC_API_DOCS_ENABLED="true"
$env:SPRINGDOC_SWAGGER_UI_ENABLED="true"
```

PostgreSQL profile 常用配置：

```powershell
$env:SPRING_PROFILES_ACTIVE="postgres"
$env:POSTGRES_URL="jdbc:postgresql://localhost:5432/contractsys"
$env:POSTGRES_USERNAME="contractsys"
$env:POSTGRES_PASSWORD="contractsys"
```

Redis 缓存配置：

```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD=""
```

## 测试

后端测试：

```powershell
cd backend
mvn test
```

前端构建校验：

```powershell
cd frontend
npm run build
```

当前测试重点覆盖：

- 权限矩阵和越权访问。
- 合同流程边界和异常分支。
- 演示数据填充与普通启动清理。
- 输入边界、附件校验、CSV 导出和登录态处理。

## 架构说明

后端采用 Controller -> Service -> Repository 分层，核心领域按职责拆分：

```text
auth      认证、JWT、当前用户解析、权限注解
contract  合同流程、查询、统计、导出、附件和访问守卫
customer  客户管理和引用检查
user      用户、角色、权限管理
log       操作日志事件监听、查询和导出
common    通用响应、异常处理、业务编号、领域事件
config    安全、缓存、数据初始化、Web 配置
```

写操作通过领域事件解耦操作日志和统计缓存失效，避免业务服务直接关心日志落库和缓存名称。合同查询、统计、导出、访问控制和流程状态机已拆分为独立服务，减少单个服务的职责膨胀。

## 开发注意事项

- 新增接口应补充 `@RequirePermission`，避免权限边界漂移。
- 合同流程写操作应通过带锁的聚合入口执行，避免并发状态机竞态。
- 附件物理文件删除应放在事务提交后执行，避免数据库回滚后文件丢失。
- 用户输入导出的 CSV 字段需要转义并防护公式注入。
- 生产环境不要使用默认 JWT secret 或默认管理员密码。
