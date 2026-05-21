# 合同管理系统

基于 Spring Boot + Vue 的合同管理系统，覆盖用户注册登录、权限、客户管理、合同起草、分配、会签、定稿、审批、签订、查询和日志等核心能力。

## 目录结构

```text
backend/   Spring Boot 后端
frontend/  Vue 3 前端
docs/      项目计划、接口、数据库、前端和测试文档
```

## 后端启动

```powershell
cd backend
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

开发环境默认使用 H2 内存数据库，并通过 Flyway 初始化表结构；联调或部署可切换到 MySQL profile。

认证使用 JWT，开发环境提供默认密钥；联调或部署时建议通过环境变量覆盖：

```powershell
$env:JWT_SECRET="replace-with-at-least-32-bytes-secret"
```

默认账号：

```text
admin / 123456
```

## MySQL 联调

启动 MySQL：

```powershell
docker compose up -d mysql
```

使用 MySQL profile 启动后端：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:MYSQL_URL="jdbc:mysql://localhost:3306/contractsys?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USERNAME="contractsys"
$env:MYSQL_PASSWORD="contractsys"
mvn spring-boot:run
```

Flyway 会自动执行 `backend/src/main/resources/db/migration/mysql` 下的建表脚本。

## 前端启动

```powershell
cd frontend
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```
