package vn.vigo.nepnha.data.repo

import kotlinx.coroutines.flow.Flow
import vn.vigo.nepnha.data.db.ChecklistItemEntity
import vn.vigo.nepnha.data.db.TaskDao
import vn.vigo.nepnha.data.db.TaskEntity
import vn.vigo.nepnha.data.db.TaskWithProgress
import java.time.LocalDate
import java.util.UUID

object TaskStatus {
    const val TODO = "todo"
    const val DOING = "doing"
    const val DONE = "done"

    fun label(s: String) = when (s) {
        DOING -> "Đang làm"
        DONE -> "Đã xong"
        else -> "Chưa làm"
    }
}

data class TaskInput(
    val id: String?,
    val title: String,
    val assignee: String?,
    val due: LocalDate?,
    val status: String,
    val eventId: String?,
    val isTemplate: Boolean,
    val note: String?,
    val checklist: List<Pair<String, Boolean>>,
)

class TaskRepository(private val dao: TaskDao) {

    fun tasks(templates: Boolean): Flow<List<TaskWithProgress>> = dao.observe(templates)

    val openCount: Flow<Int> = dao.observeOpenCount()

    suspend fun load(id: String): Pair<TaskEntity, List<ChecklistItemEntity>>? {
        val t = dao.get(id) ?: return null
        return t to dao.items(id)
    }

    suspend fun save(input: TaskInput): SaveResult {
        if (input.title.isBlank()) return SaveResult.Invalid("Vui lòng nhập tên công việc")
        if (input.checklist.size > 200) return SaveResult.Invalid("Danh sách chuẩn bị quá dài")
        val now = System.currentTimeMillis()
        val old = input.id?.let { dao.get(it) }
        val id = old?.id ?: UUID.randomUUID().toString()
        val items = input.checklist.filter { it.first.isNotBlank() }.mapIndexed { i, (text, done) ->
            ChecklistItemEntity(UUID.randomUUID().toString(), id, text.trim(), done, i)
        }
        val allDone = items.isNotEmpty() && items.all { it.done }
        val status = if (allDone && !input.isTemplate) TaskStatus.DONE else input.status
        dao.save(
            TaskEntity(
                id = id,
                title = input.title.trim(),
                assignee = input.assignee?.trim()?.takeIf { it.isNotEmpty() },
                dueEpochDay = input.due?.toEpochDay(),
                status = status,
                eventId = input.eventId,
                isTemplate = input.isTemplate,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            ),
            items,
        )
        return SaveResult.Saved(id)
    }

    /** Tạo công việc mới từ một mẫu đã lưu (checklist đặt lại chưa xong). */
    suspend fun createFromTemplate(templateId: String, due: LocalDate?): SaveResult {
        val (t, items) = load(templateId) ?: return SaveResult.Invalid("Không tìm thấy mẫu")
        return save(
            TaskInput(null, t.title, t.assignee, due, TaskStatus.TODO, t.eventId, false, t.note,
                items.map { it.text to false }),
        )
    }

    suspend fun setStatus(id: String, status: String) = dao.setStatus(id, status, System.currentTimeMillis())

    suspend fun delete(id: String) = dao.softDelete(id, System.currentTimeMillis())
}
