package com.jian.nemo2.core.domain.model

/**
 * 评分后的动作评估结果
 * 镜像自 Web 端 RatingAction
 */
sealed class RatingAction {
    /** 重新入队（继续在学习/重学阶段步进） */
    data class Requeue(val nextStep: Int, val delayMins: Int) : RatingAction()

    /** 毕业（进入长期复习阶段） */
    object Graduate : RatingAction()

    /** 水蛭处理（由于多次遗忘触发暂停或埋藏） */
    data class Leech(val action: String, val fallbackDelay: Int) : RatingAction()
}
