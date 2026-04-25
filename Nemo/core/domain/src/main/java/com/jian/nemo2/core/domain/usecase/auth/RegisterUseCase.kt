package com.jian.nemo2.core.domain.usecase.auth

import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.domain.model.User
import com.jian.nemo2.core.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, email: String, password: String): Result<User> {
        return authRepository.register(username, email, password)
    }
}
