package com.jian.nemo2.core.domain.usecase.auth

import com.jian.nemo2.core.domain.model.User
import com.jian.nemo2.core.domain.repository.AuthRepository
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): User? {
        return authRepository.getCurrentUser()
    }
}
