package com.example.inventory.domain.usecase

/**
 * UseCase 基类
 * 
 * 用于封装业务逻辑，遵循单一职责原则
 * 
 * @param P 参数类型
 * @param R 返回结果类型
 */
abstract class UseCase<in P, out R> {
    /**
     * 执行用例
     * 
     * @param params 输入参数
     * @return 执行结果
     */
    abstract suspend operator fun invoke(params: P): Result<R>
}

/**
 * 无参数的 UseCase
 * 
 * @param R 返回结果类型
 */
abstract class NoParamUseCase<out R> {
    /**
     * 执行用例
     * 
     * @return 执行结果
     */
    abstract suspend operator fun invoke(): Result<R>
}
