# Monorepo 空骨架实施计划

> **For agentic workers:** 必需子技能：使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。

**目标：** 只建立多模块项目的目录边界和 Maven POM，不提前创建组件实现、业务代码、资源配置、前端代码或部署文件。

**架构：** `backend/pom.xml` 负责聚合，`backend/dependencies/pom.xml` 统一依赖和插件版本并作为 Java 模块父 POM，`backend/components/pom.xml` 聚合后续逐个增加的组件，业务服务位于 `backend/services/<service-name>`。前端只保留 `frontend/web` 边界，开发范式后续确认。

**技术栈：** Maven 多模块、Java 和 Spring Boot 版本在执行前确认

---

### 任务 1：创建仓库级说明

**文件：**
- 新建：`README.md`
- 新建：`.gitignore`
- 新建：`.editorconfig`

- [ ] **步骤 1：README 记录后端组件、业务服务和前端三个项目边界**
- [ ] **步骤 2：配置 Java、Node、IDE 和操作系统文件的忽略规则**
- [ ] **步骤 3：配置 UTF-8、LF 和文件末尾换行**
- [ ] **步骤 4：运行 `git diff --check`，预期无空白错误**

### 任务 2：创建后端 Maven 管理骨架

**文件：**
- 新建：`backend/pom.xml`
- 新建：`backend/dependencies/pom.xml`
- 新建：`backend/components/pom.xml`
- 新建：`backend/.mvn/wrapper/maven-wrapper.properties`
- 新建：`backend/mvnw`
- 新建：`backend/mvnw.cmd`

- [ ] **步骤 1：创建只负责模块聚合的 `backend/pom.xml`**
- [ ] **步骤 2：创建包含 properties、dependencyManagement 和 pluginManagement 的 dependencies POM**
- [ ] **步骤 3：创建直接继承 dependencies POM、暂不包含具体组件 module 的 components POM**
- [ ] **步骤 4：生成 Maven Wrapper**
- [ ] **步骤 5：运行 Maven validate，预期空骨架构建成功**

### 任务 3：按需登记首个业务服务

**文件：**
- 新建：`backend/services/<service-name>/pom.xml`
- 修改：`backend/pom.xml`

- [ ] **步骤 1：确认实际服务名、groupId、artifactId 和基础包名**
- [ ] **步骤 2：只创建服务目录与 POM，不创建 `src` 内容**
- [ ] **步骤 3：把服务路径加入 backend 聚合 POM**
- [ ] **步骤 4：运行 Maven validate，预期服务空模块参与 Reactor 构建**

业务源码后续按 [项目结构设计](../../architecture/project-structure.md) 中的 `controller/service/dao/dto/remote/mq/config` 结构逐步实现。

### 任务 4：按需登记首个组件

**文件：**
- 新建：`backend/components/<component-name>/pom.xml`
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`

- [ ] **步骤 1：确认组件职责、目录名和 artifactId**
- [ ] **步骤 2：只创建组件目录与 POM，不创建 `src` 内容**
- [ ] **步骤 3：把组件加入 components 聚合 POM**
- [ ] **步骤 4：在 dependencies POM 中登记组件版本**
- [ ] **步骤 5：运行 Maven validate，预期组件空模块参与 Reactor 构建**

自动配置、配置属性和测试留到该组件的独立实施步骤中完成。
