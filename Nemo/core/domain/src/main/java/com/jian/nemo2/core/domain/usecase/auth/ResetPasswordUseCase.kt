package com.jian.nemo2.core.domain.usecase.auth

import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.domain.repository.AuthRepository
import javax.inject.Inject

class ResetPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        return authRepository.requestPasswordReset(email)
    }
}
