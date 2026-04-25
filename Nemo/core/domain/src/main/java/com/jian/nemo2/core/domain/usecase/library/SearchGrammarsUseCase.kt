package com.jian.nemo2.core.domain.usecase.library

import com.jian.nemo2.core.domain.model.Grammar
import com.jian.nemo2.core.domain.repository.GrammarRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchGrammarsUseCase @Inject constructor(
    private val grammarRepository: GrammarRepository
) {
    operator fun invoke(query: String): Flow<List<Grammar>> {
        return grammarRepository.searchGrammars(query)
    }
}
