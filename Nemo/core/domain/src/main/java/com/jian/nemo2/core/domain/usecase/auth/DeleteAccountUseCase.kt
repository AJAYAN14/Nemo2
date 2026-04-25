package com.jian.nemo2.core.domain.usecase.auth

import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.domain.repository.AuthRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(password: String): Result<Unit> {
        return authRepository.deleteAccount(password)
    }
}
