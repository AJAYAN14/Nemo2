package com.jian.nemo2.core.domain.usecase.auth

import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.domain.model.User
import com.jian.nemo2.core.domain.repository.AuthRepository

import javax.inject.Inject

/**
 * 登录并尝试恢复数据的 UseCase
 */
class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        return authRepository.login(email, password)
    }
}
