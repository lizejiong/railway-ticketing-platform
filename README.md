# Railway Platform

这是一个逐步建设的前后端 Monorepo。目前已建立后端 Maven 多模块结构、`base` 组件和 `convention` 规约组件，业务服务与前端工程将继续按模块演进。

## 当前结构

```text
backend/
├── pom.xml
├── dependencies/
│   └── pom.xml
├── parent/
│   └── pom.xml
├── components/
│   ├── pom.xml
│   ├── base/
│   │   └── pom.xml
│   └── convention/
│       └── pom.xml
└── services/

frontend/
└── web/
```

- `backend/pom.xml`：聚合所有后端 Maven 模块。
- `backend/dependencies/pom.xml`：纯 BOM，统一管理 Spring Boot、第三方依赖和内部组件版本。
- `backend/parent/pom.xml`：统一管理 Java、编码和 Maven 插件等构建约定，并导入 dependencies BOM。
- `backend/components/pom.xml`：聚合逐个增加的后端组件与 Spring Boot Starter。
- `backend/components/base`：基础常量、单例容器、启动事件和基础自动配置。
- `backend/components/convention`：错误码、异常、分页和公共响应契约，不依赖 Web 或 ORM。
- `backend/services`：后续按业务服务名称增加独立微服务模块。
- `frontend/web`：仅保留前端项目边界，开发范式后续确认。

详细设计见 [docs/architecture/project-structure.md](docs/architecture/project-structure.md)。

## 验证

```bash
cd backend
./mvnw validate
```
