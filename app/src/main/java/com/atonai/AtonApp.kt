package com.atonai

import android.content.Context
import com.atonai.data.ProviderRegistry
import com.atonai.data.SettingsStore
import com.atonai.data.SkillAssets
import com.atonai.data.WorkspaceManager
import com.atonai.data.prefs.PrefsStore
import com.atonai.data.prefs.SecretStore
import com.atonai.data.perms.PermRule
import com.atonai.data.perms.PermStore
import com.atonai.engine.Engine
import com.atonai.engine.SessionRepo
import com.atonai.engine.llm.Llm
import com.atonai.engine.perm.PermQueue
import com.atonai.engine.tools.RegistryRunner
import com.atonai.engine.tools.ToolRegistry
import com.atonai.mcp.McpClient
import com.atonai.mcp.McpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * A tiny hand-rolled dependency graph. Keeping it explicit avoids a DI
 * framework dependency while staying testable.
 */
class AppGraph(context: Context) {
    val prefs = PrefsStore(context)
    val settings = SettingsStore(context)
    val secrets = SecretStore(context)
    val registry = ProviderRegistry(context)
    val workspace = WorkspaceManager(context)
    val llm = Llm()

    /** Root that holds one folder per session (M6 sandbox). */
    val workspaceRoot: File = File(context.filesDir, "workspace").apply { mkdirs() }

    /** Permission rules (M7) and the single outstanding question. */
    val permStore = PermStore(context)
    val permQueue = PermQueue()

    /** Bundled skills and their `<available_skills>` prompt block (M10). */
    val skills = SkillAssets(context)

    /**
     * Live copy of [permStore] kept in memory so the permission gate never has
     * to block on disk I/O mid-turn. Updated by [warmPermissions] at startup.
     */
    private val permCache = java.util.concurrent.atomic.AtomicReference(emptyList<PermRule>())

    /** Tool set shared by every turn; stateless, so one instance is enough. */
    val toolRegistry = ToolRegistry(ToolRegistry.defaults(skills.library))

    val engine = Engine(
        repo = SessionRepo(File(context.filesDir, "sessions")),
        registry = registry,
        secrets = secrets,
        llm = llm,
    )
    val mcpClient = McpClient()
    val mcpRepo = McpRepository(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch { permStore.flow.collect { permCache.set(it) } }
    }

    /** A fresh runner per turn, wired to the live permission rules. */
    fun runner(
        askUser: (suspend (com.atonai.engine.tools.Question) -> String)? = null,
        onTodos: ((List<com.atonai.engine.tools.Todo>) -> Unit)? = null,
    ): RegistryRunner = RegistryRunner(
        registry = toolRegistry,
        rules = { permCache.get() },
        queue = permQueue,
        askUser = askUser,
        onTodos = onTodos,
    )

    /**
     * The system prompt for a turn: the agent's base prompt (M10) plus the
     * `<available_skills>` block (bundled + project skills under
     * `<workdir>/.agents/skills`). Skill bodies are not included — the agent
     * pulls them on demand via the `skill` tool.
     */
    fun systemPrompt(workdir: String, agent: String = "general"): String {
        val base = registry.systemPrompt(agent)
        val skillsBlock = skills.prompt(workdir)
        return if (skillsBlock.isBlank()) base else "$base\n\n$skillsBlock"
    }
}

class AtonApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Convenience accessor used by screens and view models. */
val Context.appGraph: AppGraph
    get() = (applicationContext as AtonApp).graph
