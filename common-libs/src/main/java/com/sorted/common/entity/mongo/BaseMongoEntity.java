package com.sorted.common.entity.mongo;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@FieldNameConstants
@CompoundIndexes({
    @CompoundIndex(name = "created_modified_idx", 
                  def = "{'creation_date': -1, 'modification_date': -1}"),
    @CompoundIndex(name = "deleted_created_idx", 
                  def = "{'deleted': 1, 'creation_date': -1}")
})
public abstract class BaseMongoEntity<K> implements Serializable {

	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 1L;
//	@Setter(AccessLevel.PRIVATE)
	private K id;
	@Setter(AccessLevel.PRIVATE)
	@Indexed
	private String created_by;
	@Setter(AccessLevel.PRIVATE)
	@Indexed
	private String modified_by;
	@Setter(AccessLevel.PRIVATE)
	@Indexed
	private LocalDateTime creation_date;
	@Setter(AccessLevel.PRIVATE)
	@Indexed
	private LocalDateTime modification_date;
	@Setter(AccessLevel.PRIVATE)
	private String creation_date_str;
	@Setter(AccessLevel.PRIVATE)
	private String modification_date_str;
	@Indexed
	private boolean deleted = false;

	public void setBeforeCreate(@NonNull String cudby) {
		this.created_by = cudby;
		this.modified_by = cudby;
		LocalDateTime curDate = LocalDateTime.now();
		this.creation_date = curDate;
		this.modification_date = curDate;

		DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String curDateStr = curDate.format(pattern);
		this.creation_date_str = curDateStr;
		this.modification_date_str = curDateStr;
	}

	public void setBeforeModification(@NonNull String cudby) {
		this.modified_by = cudby;
		LocalDateTime curDate = LocalDateTime.now();
		this.modification_date = curDate;

		DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.modification_date_str = curDate.format(pattern);
	}

}
