package com.example.timerapp.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PresetDao_Impl(
  __db: RoomDatabase,
) : PresetDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPresetEntity: EntityInsertAdapter<PresetEntity>

  private val __deleteAdapterOfPresetEntity: EntityDeleteOrUpdateAdapter<PresetEntity>

  private val __updateAdapterOfPresetEntity: EntityDeleteOrUpdateAdapter<PresetEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPresetEntity = object : EntityInsertAdapter<PresetEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `presets` (`id`,`label`,`durationMinutes`,`isDefault`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PresetEntity) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.label)
        statement.bindLong(3, entity.durationMinutes.toLong())
        val _tmp: Int = if (entity.isDefault) 1 else 0
        statement.bindLong(4, _tmp.toLong())
      }
    }
    this.__deleteAdapterOfPresetEntity = object : EntityDeleteOrUpdateAdapter<PresetEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `presets` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PresetEntity) {
        statement.bindLong(1, entity.id.toLong())
      }
    }
    this.__updateAdapterOfPresetEntity = object : EntityDeleteOrUpdateAdapter<PresetEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `presets` SET `id` = ?,`label` = ?,`durationMinutes` = ?,`isDefault` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PresetEntity) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.label)
        statement.bindLong(3, entity.durationMinutes.toLong())
        val _tmp: Int = if (entity.isDefault) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        statement.bindLong(5, entity.id.toLong())
      }
    }
  }

  public override suspend fun insert(preset: PresetEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfPresetEntity.insert(_connection, preset)
  }

  public override suspend fun delete(preset: PresetEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfPresetEntity.handle(_connection, preset)
  }

  public override suspend fun update(preset: PresetEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __updateAdapterOfPresetEntity.handle(_connection, preset)
  }

  public override fun getAll(): Flow<List<PresetEntity>> {
    val _sql: String = "SELECT * FROM presets ORDER BY id ASC"
    return createFlow(__db, false, arrayOf("presets")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "durationMinutes")
        val _columnIndexOfIsDefault: Int = getColumnIndexOrThrow(_stmt, "isDefault")
        val _result: MutableList<PresetEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PresetEntity
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpDurationMinutes: Int
          _tmpDurationMinutes = _stmt.getLong(_columnIndexOfDurationMinutes).toInt()
          val _tmpIsDefault: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDefault).toInt()
          _tmpIsDefault = _tmp != 0
          _item = PresetEntity(_tmpId,_tmpLabel,_tmpDurationMinutes,_tmpIsDefault)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
