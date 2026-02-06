package com.example.inventory.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 撤销/重做管理器（Actor 模式实现）
 *
 * 使用 Actor 模式确保所有操作串行执行，避免并发问题：
 * - 无锁编程，避免死锁和竞态条件
 * - 状态管理集中，易于调试
 * - 天然支持 suspend 函数
 * - 容易扩展新功能
 *
 * @param T 操作返回的数据类型
 * @param maxHistorySize 最大历史记录数量，默认 50
 */
class UndoRedoManager<T>(private val maxHistorySize: Int = 50) {

    /**
     * 可撤销的操作接口
     *
     * 实现此接口定义具体的撤销/重做逻辑
     */
    interface UndoableAction<T> {
        /** 操作描述，用于显示在历史记录中 */
        val description: String

        /**
         * 执行操作
         * @return 操作结果
         */
        suspend fun execute(): T

        /**
         * 撤销操作
         * @return 撤销结果（通常与 execute 返回相同类型）
         */
        suspend fun undo(): T
    }

    /**
     * Actor 命令密封类
     *
     * 定义所有可以发送给 Actor 的命令
     */
    private sealed class Command<T> {
        /**
         * 执行新操作
         * @param action 要执行的操作
         * @param result 用于返回结果的 CompletableDeferred
         */
        class Execute<T>(
            val action: UndoableAction<T>,
            val result: CompletableDeferred<T>
        ) : Command<T>()

        /**
         * 撤销最后一个操作
         * @param result 用于返回结果的 CompletableDeferred，返回 null 表示没有可撤销的操作
         */
        class Undo<T>(val result: CompletableDeferred<T?>) : Command<T>()

        /**
         * 重做最后一个撤销的操作
         * @param result 用于返回结果的 CompletableDeferred，返回 null 表示没有可重做的操作
         */
        class Redo<T>(val result: CompletableDeferred<T?>) : Command<T>()

        /**
         * 获取可撤销操作的历史记录列表
         * @param result 用于返回结果的 CompletableDeferred
         */
        class GetUndoHistory<T>(val result: CompletableDeferred<List<String>>) : Command<T>()

        /**
         * 获取可重做操作的历史记录列表
         * @param result 用于返回结果的 CompletableDeferred
         */
        class GetRedoHistory<T>(val result: CompletableDeferred<List<String>>) : Command<T>()

        /**
         * 清空所有历史记录
         * @param result 用于返回结果的 CompletableDeferred
         */
        class Clear<T>(val result: CompletableDeferred<Unit>) : Command<T>()

        /**
         * 获取历史记录数量
         * @param result 用于返回结果的 CompletableDeferred
         */
        class GetHistorySize<T>(val result: CompletableDeferred<Int>) : Command<T>()

        /**
         * 获取重做栈大小
         * @param result 用于返回结果的 CompletableDeferred
         */
        class GetRedoSize<T>(val result: CompletableDeferred<Int>) : Command<T>()
    }

    /** Actor 运行的协程作用域 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 是否可以撤销的状态流 */
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** 是否可以重做的状态流 */
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /**
     * Actor 实例
     *
     * 所有状态操作都在这个 Actor 中串行执行
     * 使用 BUFFERED 通道避免发送阻塞
     */
    @OptIn(ObsoleteCoroutinesApi::class)
    private val actor = scope.actor<Command<T>>(capacity = Channel.BUFFERED) {
        // 使用 ArrayDeque 作为栈，性能优于 MutableList
        val historyStack = ArrayDeque<UndoableAction<T>>()
        val redoStack = ArrayDeque<UndoableAction<T>>()

        /**
         * 更新状态流
         * 在 Actor 内部调用，无需加锁
         */
        fun updateState() {
            _canUndo.value = historyStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
        }

        // 处理所有发送给 Actor 的命令
        for (command in channel) {
            when (command) {
                is Command.Execute -> {
                    try {
                        // 1. 执行操作
                        val result = command.action.execute()

                        // 2. 更新状态
                        historyStack.addLast(command.action)
                        if (historyStack.size > maxHistorySize) {
                            historyStack.removeFirst()
                        }
                        redoStack.clear()
                        updateState()

                        // 3. 返回结果
                        command.result.complete(result)
                    } catch (e: Exception) {
                        command.result.completeExceptionally(e)
                    }
                }

                is Command.Undo -> {
                    if (historyStack.isEmpty()) {
                        command.result.complete(null)
                        continue
                    }
                    try {
                        // 1. 移除并执行撤销
                        val action = historyStack.removeLast()
                        val result = action.undo()

                        // 2. 更新状态
                        redoStack.addLast(action)
                        updateState()

                        // 3. 返回结果
                        command.result.complete(result)
                    } catch (e: Exception) {
                        command.result.completeExceptionally(e)
                    }
                }

                is Command.Redo -> {
                    if (redoStack.isEmpty()) {
                        command.result.complete(null)
                        continue
                    }
                    try {
                        // 1. 移除并执行重做
                        val action = redoStack.removeLast()
                        val result = action.execute()

                        // 2. 更新状态
                        historyStack.addLast(action)
                        updateState()

                        // 3. 返回结果
                        command.result.complete(result)
                    } catch (e: Exception) {
                        command.result.completeExceptionally(e)
                    }
                }

                is Command.GetUndoHistory -> {
                    command.result.complete(historyStack.map { it.description })
                }

                is Command.GetRedoHistory -> {
                    command.result.complete(redoStack.map { it.description })
                }

                is Command.Clear -> {
                    historyStack.clear()
                    redoStack.clear()
                    updateState()
                    command.result.complete(Unit)
                }

                is Command.GetHistorySize -> {
                    command.result.complete(historyStack.size)
                }

                is Command.GetRedoSize -> {
                    command.result.complete(redoStack.size)
                }
            }
        }
    }

    /**
     * 执行操作并记录到历史
     *
     * @param action 可撤销的操作
     * @return 操作结果
     * @throws Exception 当操作执行失败时抛出
     */
    suspend fun execute(action: UndoableAction<T>): T {
        val result = CompletableDeferred<T>()
        actor.send(Command.Execute(action, result))
        return result.await()
    }

    /**
     * 撤销最后一个操作
     *
     * @return 撤销操作的结果，如果没有可撤销的操作返回 null
     * @throws Exception 当撤销操作执行失败时抛出
     */
    suspend fun undo(): T? {
        val result = CompletableDeferred<T?>()
        actor.send(Command.Undo(result))
        return result.await()
    }

    /**
     * 重做最后一个撤销的操作
     *
     * @return 重做操作的结果，如果没有可重做的操作返回 null
     * @throws Exception 当重做操作执行失败时抛出
     */
    suspend fun redo(): T? {
        val result = CompletableDeferred<T?>()
        actor.send(Command.Redo(result))
        return result.await()
    }

    /**
     * 获取可撤销的操作列表（用于显示历史记录）
     *
     * @return 操作描述列表，从旧到新排序
     */
    suspend fun getUndoHistory(): List<String> {
        val result = CompletableDeferred<List<String>>()
        actor.send(Command.GetUndoHistory(result))
        return result.await()
    }

    /**
     * 获取可重做的操作列表
     *
     * @return 操作描述列表，从旧到新排序
     */
    suspend fun getRedoHistory(): List<String> {
        val result = CompletableDeferred<List<String>>()
        actor.send(Command.GetRedoHistory(result))
        return result.await()
    }

    /**
     * 清空所有历史记录
     */
    suspend fun clear() {
        val result = CompletableDeferred<Unit>()
        actor.send(Command.Clear(result))
        result.await()
    }

    /**
     * 获取历史记录数量
     *
     * @return 当前历史记录栈的大小
     */
    suspend fun getHistorySize(): Int {
        val result = CompletableDeferred<Int>()
        actor.send(Command.GetHistorySize(result))
        return result.await()
    }

    /**
     * 获取重做栈大小
     *
     * @return 当前重做栈的大小
     */
    suspend fun getRedoSize(): Int {
        val result = CompletableDeferred<Int>()
        actor.send(Command.GetRedoSize(result))
        return result.await()
    }

    /**
     * 关闭管理器
     *
     * 释放所有资源，关闭 Actor 和协程作用域
     * 调用后不能再使用此实例
     */
    fun close() {
        actor.close()
        scope.cancel()
    }
}

/**
 * 库存操作的撤销/重做管理器
 *
 * 针对库存管理业务封装的专用管理器
 * 简化了 UndoRedoManager 的使用
 *
 * @param maxHistorySize 最大历史记录数量
 */
class InventoryUndoManager(maxHistorySize: Int = Constants.Undo.MAX_HISTORY_SIZE) {

    /**
     * 库存操作类型
     *
     * 用于标识不同类型的库存操作
     */
    sealed class InventoryActionType {
        data class AddItem(val itemId: Long) : InventoryActionType()
        data class UpdateItem(val itemId: Long) : InventoryActionType()
        data class DeleteItem(val itemId: Long) : InventoryActionType()
        data class BatchDelete(val count: Int) : InventoryActionType()
        data class UpdateQuantity(val itemId: Long) : InventoryActionType()
    }

    /** 内部使用的 UndoRedoManager 实例 */
    private val undoManager = UndoRedoManager<InventoryActionType>(maxHistorySize)

    /** 是否可以撤销 */
    val canUndo: StateFlow<Boolean> = undoManager.canUndo

    /** 是否可以重做 */
    val canRedo: StateFlow<Boolean> = undoManager.canRedo

    /**
     * 记录添加商品操作
     *
     * @param itemId 商品 ID
     * @param onExecute 执行操作时的回调（实际执行业务逻辑）
     * @param onUndo 撤销操作时的回调（恢复业务状态）
     */
    suspend fun recordAddItem(
        itemId: Long,
        onExecute: suspend () -> Unit = {},
        onUndo: suspend () -> Unit
    ) {
        val action = object : UndoRedoManager.UndoableAction<InventoryActionType> {
            override val description = "添加商品 #$itemId"

            override suspend fun execute(): InventoryActionType {
                onExecute()
                return InventoryActionType.AddItem(itemId)
            }

            override suspend fun undo(): InventoryActionType {
                onUndo()
                return InventoryActionType.DeleteItem(itemId)
            }
        }

        undoManager.execute(action)
    }

    /**
     * 记录删除商品操作
     *
     * @param itemId 商品 ID
     * @param onExecute 执行操作时的回调
     * @param onUndo 撤销操作时的回调（恢复商品）
     */
    suspend fun recordDeleteItem(
        itemId: Long,
        onExecute: suspend () -> Unit = {},
        onUndo: suspend () -> Unit
    ) {
        val action = object : UndoRedoManager.UndoableAction<InventoryActionType> {
            override val description = "删除商品 #$itemId"

            override suspend fun execute(): InventoryActionType {
                onExecute()
                return InventoryActionType.DeleteItem(itemId)
            }

            override suspend fun undo(): InventoryActionType {
                onUndo()
                return InventoryActionType.AddItem(itemId)
            }
        }

        undoManager.execute(action)
    }

    /**
     * 记录更新商品操作
     *
     * @param itemId 商品 ID
     * @param onExecute 执行操作时的回调（应用新值）
     * @param onUndo 撤销操作时的回调（恢复旧值）
     */
    suspend fun recordUpdateItem(
        itemId: Long,
        onExecute: suspend () -> Unit,
        onUndo: suspend () -> Unit
    ) {
        val action = object : UndoRedoManager.UndoableAction<InventoryActionType> {
            override val description = "更新商品 #$itemId"

            override suspend fun execute(): InventoryActionType {
                onExecute()
                return InventoryActionType.UpdateItem(itemId)
            }

            override suspend fun undo(): InventoryActionType {
                onUndo()
                return InventoryActionType.UpdateItem(itemId)
            }
        }

        undoManager.execute(action)
    }

    /**
     * 记录批量删除操作
     *
     * @param itemIds 删除的商品 ID 列表
     * @param onExecute 执行操作时的回调
     * @param onUndo 撤销操作时的回调（恢复所有商品）
     */
    suspend fun recordBatchDelete(
        itemIds: List<Long>,
        onExecute: suspend () -> Unit = {},
        onUndo: suspend () -> Unit
    ) {
        val action = object : UndoRedoManager.UndoableAction<InventoryActionType> {
            override val description = "批量删除 ${itemIds.size} 个商品"

            override suspend fun execute(): InventoryActionType {
                onExecute()
                return InventoryActionType.BatchDelete(itemIds.size)
            }

            override suspend fun undo(): InventoryActionType {
                onUndo()
                return InventoryActionType.BatchDelete(itemIds.size)
            }
        }

        undoManager.execute(action)
    }

    /**
     * 记录更新库存数量操作
     *
     * @param itemId 商品 ID
     * @param onExecute 执行操作时的回调
     * @param onUndo 撤销操作时的回调
     */
    suspend fun recordUpdateQuantity(
        itemId: Long,
        onExecute: suspend () -> Unit,
        onUndo: suspend () -> Unit
    ) {
        val action = object : UndoRedoManager.UndoableAction<InventoryActionType> {
            override val description = "更新商品 #$itemId 库存"

            override suspend fun execute(): InventoryActionType {
                onExecute()
                return InventoryActionType.UpdateQuantity(itemId)
            }

            override suspend fun undo(): InventoryActionType {
                onUndo()
                return InventoryActionType.UpdateQuantity(itemId)
            }
        }

        undoManager.execute(action)
    }

    /**
     * 撤销最后一个操作
     *
     * @return 被撤销的操作类型，如果没有可撤销的操作返回 null
     */
    suspend fun undo(): InventoryActionType? = undoManager.undo()

    /**
     * 重做最后一个撤销的操作
     *
     * @return 被重做的操作类型，如果没有可重做的操作返回 null
     */
    suspend fun redo(): InventoryActionType? = undoManager.redo()

    /**
     * 获取撤销历史记录
     *
     * @return 操作描述列表
     */
    suspend fun getHistory(): List<String> = undoManager.getUndoHistory()

    /**
     * 清空所有历史记录
     */
    suspend fun clear() = undoManager.clear()

    /**
     * 关闭管理器
     */
    fun close() = undoManager.close()
}
