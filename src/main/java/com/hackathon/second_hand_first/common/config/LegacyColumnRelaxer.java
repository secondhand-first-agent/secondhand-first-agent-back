package com.hackathon.second_hand_first.common.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 옛 스키마로 만들어진 DB 의 <b>NOT NULL 제약을 푼다.</b>
 *
 * <p>{@code ddl-auto: update} 는 테이블과 컬럼을 <b>추가</b>할 뿐, 이미 있는 컬럼의
 * NOT NULL 을 풀지 않는다. 기존 데이터를 깨뜨릴 수 있는 변경을 하지 않기 때문이다.
 *
 * <p>그래서 «컬럼을 nullable 로 바꾸는» 변경은 코드만 고쳐서는 반영되지 않는다.
 * 새로 만든 DB 는 멀쩡하고 <b>운영 DB 만 깨진다</b> — 실제로 그렇게 됐다.
 *
 * <pre>
 * Column 'external_view_count' cannot be null
 * </pre>
 *
 * <p>여기서 필요한 컬럼만 골라 한 번 풀어 준다. 이미 nullable 이면 아무것도 하지 않으므로
 * 매번 떠도 안전하다.
 *
 * <p><b>새 제약을 거는 데는 쓰지 않는다.</b> 그건 데이터를 잃을 수 있어 사람이 판단해야 한다.
 * 여기는 «푸는 것»만 한다.
 */
@Component
@RequiredArgsConstructor
public class LegacyColumnRelaxer {

    private static final Logger log = LoggerFactory.getLogger(LegacyColumnRelaxer.class);

    /**
     * 풀어야 하는 컬럼 목록.
     *
     * <p>{@code external_view_count} — 조회수를 수집하지 못하는 경로가 있어 nullable 로
     * 바꿨다. 0 으로 채우면 «조회 0회»와 «수집 못 함»이 구분되지 않는다.
     */
    private static final List<Column> COLUMNS = List.of(
            new Column("products", "external_view_count", "BIGINT")
    );

    private final JdbcTemplate jdbcTemplate;

    private record Column(String table, String name, String type) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void relax() {
        for (Column column : COLUMNS) {
            try {
                if (isNotNull(column)) {
                    jdbcTemplate.execute(
                            "ALTER TABLE %s MODIFY %s %s NULL"
                                    .formatted(column.table(), column.name(), column.type())
                    );
                    log.warn(
                            "옛 스키마의 NOT NULL 을 풀었습니다 — {}.{}. "
                                    + "ddl-auto 는 제약 완화를 하지 않아 여기서 처리합니다.",
                            column.table(), column.name()
                    );
                }
            } catch (Exception exception) {
                // 실패해도 기동을 막지 않는다. 권한이 없거나 다른 DB 엔진일 수 있다.
                // 문제가 있으면 첫 저장에서 드러나므로 여기서 죽일 이유가 없다.
                log.warn(
                        "{}.{} 제약 확인·완화 실패 — {}",
                        column.table(), column.name(), exception.toString()
                );
            }
        }
    }

    /**
     * 지금 NOT NULL 인지 본다.
     *
     * <p>{@code information_schema} 는 MySQL·H2·PostgreSQL 이 모두 제공하는 표준
     * 뷰다. 테이블이 아직 없으면 결과가 비어 «풀 것이 없다»로 판정된다.
     */
    private boolean isNotNull(Column column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_name = ? AND column_name = ? AND is_nullable = 'NO'
                """,
                Integer.class,
                column.table().toUpperCase(),
                column.name().toUpperCase()
        );
        if (count != null && count > 0) {
            return true;
        }
        // MySQL 은 소문자로 저장한다. 대소문자를 모두 확인한다.
        count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_name = ? AND column_name = ? AND is_nullable = 'NO'
                """,
                Integer.class,
                column.table(),
                column.name()
        );
        return count != null && count > 0;
    }
}
