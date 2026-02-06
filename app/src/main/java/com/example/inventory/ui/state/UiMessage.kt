package com.example.inventory.ui.state

/**
 * UI 消息类型
 * 
 * 用于在 UI 层显示不同类型的消息提示
 */
sealed class UiMessage {
    /**
     * 成功消息
     * 
     * @param text 消息文本
     */
    data class Success(val text: String) : UiMessage()
    
    /**
     * 错误消息
     * 
     * @param text 消息文本
     */
    data class Error(val text: String) : UiMessage()
    
    /**
     * 信息消息
     * 
     * @param text 消息文本
     */
    data class Info(val text: String) : UiMessage()
    
    /**
     * 警告消息
     * 
     * @param text 消息文本
     */
    data class Warning(val text: String) : UiMessage()
}
