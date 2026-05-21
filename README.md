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

开发环境默认使用 H2 内存数据库，同时保留 MySQL 驱动，后续可切换到 MySQL。

默认账号：

```text
admin / 123456
```

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

