package com.jian.nemo2.core.domain.usecase.auth

import com.jian.nemo2.core.domain.model.User
import com.jian.nemo2.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUserFlowUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<User?> {
        return authRepository.getUserFlow()
    }
}
