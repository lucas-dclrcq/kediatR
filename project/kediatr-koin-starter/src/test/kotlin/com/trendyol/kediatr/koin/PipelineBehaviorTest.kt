package com.trendyol.kediatr.koin

import com.trendyol.kediatr.CommandHandler
import com.trendyol.kediatr.CommandWithResultHandler
import com.trendyol.kediatr.NotificationHandler
import com.trendyol.kediatr.PipelineBehavior
import com.trendyol.kediatr.QueryHandler
import com.trendyol.kediatr.Command
import com.trendyol.kediatr.Mediator
import com.trendyol.kediatr.RequestHandlerDelegate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.junit5.KoinTestExtension
import kotlin.test.assertTrue

var exceptionPipelineBehaviorHandleCounter = 0
var exceptionPipelineBehaviorHandleCatchCounter = 0
var loggingPipelineBehaviorHandleBeforeNextCounter = 0
var loggingPipelineBehaviorHandleAfterNextCounter = 0

class PipelineBehaviorTest : KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(
            module {
                single { KediatRKoin.getMediator() }
                single { ExceptionPipelineBehavior() } bind ExceptionPipelineBehavior::class
                single { LoggingPipelineBehavior() } bind LoggingPipelineBehavior::class
                single { MyCommandHandler(get()) } bind CommandHandler::class
                single { MyAsyncCommandRHandler(get()) } bind CommandWithResultHandler::class
                single { MyFirstNotificationHandler(get()) } bind NotificationHandler::class
                single { TestQueryHandler(get()) } bind QueryHandler::class
            }
        )
    }

    init {
        exceptionPipelineBehaviorHandleCounter = 0
        exceptionPipelineBehaviorHandleCatchCounter = 0
        loggingPipelineBehaviorHandleBeforeNextCounter = 0
        loggingPipelineBehaviorHandleAfterNextCounter = 0
    }

    private val mediator by inject<Mediator>()

    @Test
    fun `should process command with async pipeline`() {
        runBlocking {
            mediator.send(MyCommand())
        }

        assertTrue { exceptionPipelineBehaviorHandleCatchCounter == 0 }
        assertTrue { exceptionPipelineBehaviorHandleCounter == 1 }
        assertTrue { loggingPipelineBehaviorHandleBeforeNextCounter == 1 }
        assertTrue { loggingPipelineBehaviorHandleAfterNextCounter == 1 }
    }

    @Test
    fun `should process exception in async handler`() {
        val act = suspend { mediator.send(MyBrokenCommand()) }

        assertThrows<Exception> { runBlocking { act() } }

        assertTrue { exceptionPipelineBehaviorHandleCatchCounter == 1 }
        assertTrue { exceptionPipelineBehaviorHandleCounter == 1 }
        assertTrue { loggingPipelineBehaviorHandleBeforeNextCounter == 1 }
        assertTrue { loggingPipelineBehaviorHandleAfterNextCounter == 0 }
    }
}

class MyBrokenCommand : Command

class MyBrokenHandler(
    private val mediator: Mediator,
) : CommandHandler<MyBrokenCommand> {
    override suspend fun handle(command: MyBrokenCommand) {
        delay(500)
        throw Exception()
    }
}

class ExceptionPipelineBehavior : PipelineBehavior {
    override suspend fun <TRequest, TResponse> handle(
        request: TRequest,
        next: RequestHandlerDelegate<TRequest, TResponse>,
    ): TResponse {
        try {
            exceptionPipelineBehaviorHandleCounter++
            return next(request)
        } catch (ex: Exception) {
            exceptionPipelineBehaviorHandleCatchCounter++
            throw ex
        }
    }
}

class LoggingPipelineBehavior : PipelineBehavior {
    override suspend fun <TRequest, TResponse> handle(
        request: TRequest,
        next: RequestHandlerDelegate<TRequest, TResponse>,
    ): TResponse {
        loggingPipelineBehaviorHandleBeforeNextCounter++
        val result = next(request)
        loggingPipelineBehaviorHandleAfterNextCounter++
        return result
    }
}
