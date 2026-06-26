# MyBatis-Plus 代码生成器

## 模块说明

**这是一个纯工具模块，不是 Spring Boot 应用！**

- ❌ 不需要 Spring Boot 启动类
- ❌ 不需要 web 依赖
- ❌ 不需要打包成可执行 jar
- ✅ 只需要运行 `CodeGenerator.main()` 方法即可

本模块用于根据数据库表结构自动生成 MyBatis-Plus 相关代码，包括：
- Entity（实体类）
- Mapper（数据访问接口）
- Service（业务逻辑接口和实现）
- Controller（控制器）
- Mapper XML（SQL 映射文件）

## 快速开始

### 1. 配置数据库连接

**直接编辑 `CodeGenerator.java` 文件**，修改以下常量：

```java
private static final String URL = "jdbc:mysql://localhost:3306/your_database?...";
private static final String USERNAME = "root";
private static final String PASSWORD = "your_password";
```

> **注意**：所有配置都在代码中直接修改，不需要任何配置文件！

### 2. 配置生成参数

```java
private static final String TABLE_NAMES = "*";           // 要生成的表名，* 表示所有表
private static final String MODULE_NAME = "order-service"; // 模块名称
private static final String PARENT_PACKAGE = "org.lee.rocket.train"; // 父包名
```

### 3. 运行生成器

**在 IDEA 中直接运行 `CodeGenerator.main()` 方法即可！**

右键点击 `CodeGenerator.java` → Run 'CodeGenerator.main()'

或者在命令行执行：
```bash
cd mybatis-generator
mvn exec:java -Dexec.mainClass="org.lee.rocket.train.mybatisgenerator.CodeGenerator"
```

### 4. 复制生成的代码

生成的代码位于 `mybatis-generator/src/main/java` 目录下，根据需要复制到对应的服务模块中。

## 配置说明

### 数据库配置

- **URL**: JDBC 连接字符串，确保包含正确的时区和字符集设置
- **USERNAME**: 数据库用户名
- **PASSWORD**: 数据库密码

### 生成策略

当前配置启用了以下特性：
- ✅ Lombok 注解（@Data, @Builder 等）
- ✅ 字段注解（@TableField, @TableName 等）
- ✅ 逻辑删除支持（deleted 字段）
- ✅ BaseResultMap 和 BaseColumnList
- ✅ RESTful 风格 Controller

### 自定义表前缀

如果数据库表有统一前缀（如 `t_user`, `t_order`），可以在策略配置中添加：

```java
.addTablePrefix("t_", "sys_")
```

生成器会自动去除前缀，生成的类名为 `User`, `Order` 等。

## 注意事项

1. **首次使用前**：确保 MySQL 服务已启动，数据库中存在要生成的表
2. **覆盖问题**：重复生成会覆盖已有文件，请提前备份重要代码
3. **依赖冲突**：本模块仅用于代码生成，不应被其他模块依赖
4. **版本兼容**：使用的 MyBatis-Plus Generator 版本与项目其他模块保持一致（3.5.16）

## 示例

假设数据库中有表 `t_order`：

```sql
CREATE TABLE t_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2),
    status INT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
);
```

配置：
```java
private static final String TABLE_NAMES = "t_order";
private static final String MODULE_NAME = "order-service";
```

运行后将生成：
```
org.lee.rocket.train.order.service.entity.Order
org.lee.rocket.train.order.service.mapper.OrderMapper
org.lee.rocket.train.order.service.service.OrderService
org.lee.rocket.train.order.service.service.impl.OrderServiceImpl
org.lee.rocket.train.order.service.controller.OrderController
resources/mapper/OrderMapper.xml
```

## 常见问题

### Q: 提示找不到数据库驱动？
A: 确保 pom.xml 中已添加 mysql-connector-j 依赖

### Q: 生成的代码在哪里？
A: 默认在 `mybatis-generator/src/main/java` 目录，可在 OUTPUT_DIR 配置中修改

### Q: 如何只生成特定表的代码？
A: 修改 TABLE_NAMES 为具体表名，多个表用逗号分隔：`"user,order,product"`

### Q: 可以自定义生成模板吗？
A: 可以，使用 `.templateConfig()` 方法自定义 Freemarker 模板
