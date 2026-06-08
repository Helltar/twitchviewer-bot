package com.helltar.twitchviewerbot.bot

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.commands.BotCommand
import com.helltar.twitchviewerbot.database.dao.usersDao
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.telegram.telegrambots.meta.api.methods.ParseMode
import java.util.concurrent.ConcurrentHashMap

object CommandExecutor {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val requestsMap = ConcurrentHashMap<String, Job>()

    private val log = KotlinLogging.logger {}

    fun executeCommand(botCommand: BotCommand, requestKey: String? = null) {
        val user = botCommand.ctx.user()
        val userId = user.id
        val chat = botCommand.ctx.message().chat
        val commandName = botCommand.javaClass.simpleName

        log.info { "$commandName: ${chat.id} $userId ${user.userName} ${user.firstName} ${chat.title}: ${botCommand.ctx.message().text}" }

        val launch =
            launch("${requestKey ?: commandName}@$userId") {
                usersDao.upsert(user)
                botCommand.run()
            }

        if (!launch)
            botCommand.replyToMessage(Strings.localizedString(Strings.MANY_REQUEST, user.languageCode))
    }

    fun launch(key: String, task: suspend () -> Unit): Boolean {
        if (requestsMap.containsKey(key))
            if (requestsMap[key]?.isCompleted == false)
                return false

        log.debug { "launch --> $key" }

        requestsMap[key] =
            scope.launch {
                try {
                    task()
                } catch (e: CancellationException) {
                    log.debug { "job cancelled --> $key" }
                    throw e
                } catch (e: Exception) {
                    log.error(e) { "job failed --> $key" }
                } finally {
                    requestsMap.remove(key)
                    log.debug { "remove --> $key (${requestsMap.size})" }
                }
            }

        return true
    }

    fun cancelJobs(ctx: MessageContext) {

        fun replyToMessage(text: String) =
            ctx.replyToMessage(text).setParseMode(ParseMode.HTML).callAsync(ctx.sender)

        val userId = ctx.user().id
        val languageCode = ctx.user().languageCode
        val activeJobs = requestsMap.filter { it.key.endsWith("@$userId") && it.value.isActive }

        if (activeJobs.isEmpty()) {
            replyToMessage(Strings.localizedString(Strings.NO_ACTIVE_TASKS, languageCode))
            return
        }

        activeJobs.forEach { (key, job) ->
            log.debug { "job.cancel --> $key" }
            job.cancel()
        }

        replyToMessage(Strings.localizedString(Strings.TASKS_ARE_CANCELLED, languageCode))
    }
}
