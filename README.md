# Railway Platform

这是一个逐步建设的前后端 Monorepo。目前只建立后端 Maven 多模块骨架，不包含业务代码、具体组件实现或前端工程配置。

## 当前结构

```text
backend/
├── pom.xml
├── dependencies/
│   └── pom.xml
├── components/
│   └── pom.xml
└── services/

frontend/
└── web/
```

- `backend/pom.xml`：聚合所有后端 Maven 模块。
- `backend/dependencies/pom.xml`：统一管理 Java、Spring Boot、第三方依赖和 Maven 插件版本。
- `backend/components/pom.xml`：聚合后续逐个增加的 Spring Boot Starter 组件。
- `backend/services`：后续按业务服务名称增加独立微服务模块。
- `frontend/web`：仅保留前端项目边界，开发范式后续确认。

详细设计见 [docs/architecture/project-structure.md](docs/architecture/project-structure.md)。

## 验证

```bash
cd backend
./mvnw validate
```
