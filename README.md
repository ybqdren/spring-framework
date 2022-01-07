# Spring 体系结构
![Spring5 模块结构图](https://github.com/ybqdren/spring-framework/blob/my-spring/img/spring%20%E6%A8%A1%E5%9D%97.png)

Spring 总共有大约 20 个模块，由 1300 多个不同的文件构成。
而这些组件被分别整合在 核心容器 (Conre Container) 、AOP (Aspect Oriented Programming)和设备支持（Instrmentation）、数据访问及集成（ DataAccess / Integeration ）、Web 、报文发送（ Messaging ）、Test，6个模块集合中。

组成 Spring 框架的每个模块集合或者模块都可以单独存在，也可以一个或多个模块联合组合。



# 核心容器
由 spring-beans 、spring-core 、 spring-context 和 spring-expression（Spring Expression Language,SpEL ） 4个模块组成。

spring-beans 和 spring-core 模块是 Spring 框架的核心模块，包含了`控制反转`(Inversion Of Control , IOC) 和 `依赖注入`(Dependency InJection , DI) 。
BeanFactory 接口是 Spring 框架中的核心接口，它是工厂模式的具体实现。BeanFactory 使用控制反转对应用程序的配置和依赖性规范与实际的应用程序进行了分离。
但 BeanFactory 容器实例化后并不会自动实例化 Bean ，只有当 Bean 被使用时 BeanFactory 容器才会对该 Bean 进行实例化与依赖关系的装配。


spring-context 模块构架于核心模块之上，他扩展了 BeanFactory ，为她添加了 Bean 生命周期控制、框架时间体系以及资源加载透明化等功能。
此外模块还提供了许多企业级支持，如邮件访问、远程控制、任务调度等，ApplicationContext 是该模块的核心接口，她是 BeanFactory 的超类，于 BeanFactory 不同。
ApplicationContext 容器实例化后会自动对所有的但实例 Bean 进行实例化与依赖关系的装配，使值处于待用状态。


spring-expression 模块是统一表达式语言（EL）的扩展，可以查询、管理运行中的对象，同时也方便的可以调用对象方法、操作数组、集合等。
它的语法类似于传统 EL，但提供了额外的功能，最出色的要数函数调用和简单字符串的模板函数。这种语言的特性是基于 Spring 产品的需求而设计，他可以非常方便地同 Spring IOC 进行及哦啊胡。



# AOP 和设备支持
由 spring-aop 、spring-aspects 和 spring-insrument 3个模块组成。

spring-aop 是 Spring 的另一个核心模块，是 AOP 主要的实现模块。作为继 OOP 后，对程序员影响最大的编程思想之一，AOP 极大地开拓了人们对于编程的思路。
在 Spring 中，它是以 JVM 的动态代理技术为基础，然后设计出了一系列的 AOP 横切实现，比如前置通知、返回通知、异常通知等，同时，Pointcut 接口来匹配切入点，可以使用现有的切入点来设计横切面，也可以扩展相关方法根据需求进行切入。
spring-aspect 模块集成自 AspectJ 框架，主要是为 SpringAOP 提供多种 AOP 实现方法。

spring-instrument 模块是基于 JavaSE 中的"java.lang.instrument"进行设计的，应该算是 AOP 的一个支援模块，主要作用是在 JVM 启用时，生成一个代理类，程序员通过代理类在运行时修改类的字节，从而改变一个类的功能，实现 AOP 的功能。








