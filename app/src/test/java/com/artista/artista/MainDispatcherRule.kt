package com.artista.artista

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that installs a [TestDispatcher] as `Dispatchers.Main` for the duration of a test.
 *
 * Needed to test ViewModels that launch coroutines in `viewModelScope`, which is bound to `Main`.
 *
 * @param testDispatcher the dispatcher to install as `Dispatchers.Main`
 * @author timo-by
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {

  override fun starting(description: Description) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
