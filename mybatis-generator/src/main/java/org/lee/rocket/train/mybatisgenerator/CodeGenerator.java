package org.lee.rocket.train.mybatisgenerator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 * 用于根据数据库表结构自动生成 Entity、Mapper、Service、Controller 等代码
 */
public class CodeGenerator {

    // ==================== 配置区域 ====================
    
    /**
     * 数据库连接 URL
     */
    private static final String URL = "jdbc:mysql://localhost:3306/rocket-demo-db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false";
    
    /**
     * 数据库用户名
     */
    private static final String USERNAME = "root";
    
    /**
     * 数据库密码
     */
    private static final String PASSWORD = "root";
    
    /**
     * 需要生成的表名，多个表用逗号分隔
     * 例如: "user,order,product"
     * 留空或填 "*" 表示所有表
     */
    private static final String TABLE_NAMES = "tb_goods_stocks_log";
    
    /**
     * 模块名称（影响包路径）
     * 例如: order-service, user-service
     */
    private static final String MODULE_NAME = "goods-service";
    
    /**
     * 父包名
     */
    private static final String PARENT_PACKAGE = "org.lee.rocket.train";
    
    /**
     * 代码输出目录
     */
    private static final String OUTPUT_DIR = System.getProperty("user.dir") + "/mybatis-generator/src/main/java";

    // ==================== 主方法 ====================
    
    public static void main(String[] args) {
        FastAutoGenerator.create(URL, USERNAME, PASSWORD)
                // 全局配置
                .globalConfig(builder -> {
                    builder.author("CodeGenerator")           // 作者注释
                            .outputDir(OUTPUT_DIR)             // 输出目录
                            .disableOpenDir();                 // 禁止自动打开输出目录
                })
                
                // 包配置
                .packageConfig(builder -> {
                    builder.parent(PARENT_PACKAGE)             // 父包名
                            .moduleName(MODULE_NAME)           // 模块名
                            .entity("entity")                  // Entity 包名
                            .mapper("mapper")                  // Mapper 包名
                            .service("service")                // Service 包名
                            .serviceImpl("service.impl")       // ServiceImpl 包名
                            .controller("controller")          // Controller 包名
                            .xml("mapper.xml")                 // Mapper XML 文件目录
                            .pathInfo(Collections.singletonMap(
                                    OutputFile.xml,            // 设置 mapperXml 生成路径
                                    System.getProperty("user.dir") + "/mybatis-generator/src/main/resources/mapper"
                            ));
                })
                
                // 策略配置
                .strategyConfig(builder -> {
                    builder.addInclude(getTables(TABLE_NAMES)) // 需要生成的表
                            .addTablePrefix("tb_", "sys_")      // 表前缀（会自动去除）
                            
                            // Entity 策略
                            .entityBuilder()
                            .enableLombok()                    // 启用 Lombok
                            .enableTableFieldAnnotation()      // 启用字段注解
                            .logicDeleteColumnName("deleted")  // 逻辑删除字段名
                            
                            // Mapper 策略
                            .mapperBuilder()
                            .enableBaseResultMap()             // 启用 BaseResultMap
                            .enableBaseColumnList()            // 启用 BaseColumnList
                            
                            // Service 策略
                            .serviceBuilder()
                            .formatServiceFileName("%sService") // Service 接口命名格式
                            .formatServiceImplFileName("%sServiceImpl") // ServiceImpl 命名格式
                            
                            // Controller 策略
                            .controllerBuilder()
                            .enableRestStyle();                // 启用 REST 风格
                })
                
                // 模板引擎配置
                .templateEngine(new FreemarkerTemplateEngine())
                
                // 执行
                .execute();
        
        System.out.println("========== 代码生成完成！==========");
        System.out.println("输出目录: " + OUTPUT_DIR);
    }

    /**
     * 处理表名配置
     * @param tableNames 表名配置
     * @return 表名数组
     */
    private static String[] getTables(String tableNames) {
        if (tableNames == null || tableNames.trim().isEmpty()) {
            return new String[]{}; // 空数组
        }
        return tableNames.split(",");
    }
}
