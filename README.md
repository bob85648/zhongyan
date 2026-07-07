# hubeizhongyan

武汉中烟历史传感器数据分析与展示系统。

当前仓库已初始化前后端基础开发框架，并补齐了最小可跑通的历史数据演示流程：

- `backend`：Spring Boot 3 + Maven
- `frontend`：Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router

详细启动方式见文末。

## 目录结构

```text
hubeizhongyan/
├─ backend/
├─ frontend/
└─ README.md
```

## 后端启动

```powershell
cd backend
mvn "-Dmaven.repo.local=.mvn-repo" package -DskipTests
java -jar target/historical-analysis-backend-0.0.1-SNAPSHOT.jar
```

默认端口：`8080`

说明：在当前 Windows 中文路径环境下，`spring-boot:run` 可能出现主类加载失败，推荐直接使用可执行 `jar` 启动。

测试接口：

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/api/system/info`
- `GET http://localhost:8080/api/demo/overview`
- `GET http://localhost:8080/h2-console`

## 前端启动

```powershell
cd frontend
npm install
npm run dev
```

默认端口：`5173`

前端开发环境已配置代理到 `http://localhost:8080`。

## 当前已完成内容

1. 后端基础工程结构与配置。
2. 统一响应体与全局异常处理。
3. 基础健康检查与系统信息示例接口。
4. 内置 H2 演示数据库与最小表结构。
5. 启动时自动生成工序、变量、批次、时序点和统计结果测试数据。
6. 前端基础布局、路由、状态管理与 API 封装。
7. 首页演示分析页，可直接查看单批次趋势、多批次对比和批次统计表。
8. 导入任务页，可上传演示文件并自动生成新的模拟批次数据。
