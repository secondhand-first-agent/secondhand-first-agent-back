package com.hackathon.second_hand_first.product.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정하지 않았을 때 <b>서버가 정상적으로 뜨는지</b> 본다.
 *
 * <p>컨트롤러는 있는데 구현체 빈이 없으면 기동에 실패한다. 조건을 한쪽에만 걸면
 * 벌어지는 일이라, 기본값 상태를 실제로 띄워 확인한다.
 */
@SpringBootTest
class ProductSearchDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("설정이 없으면 검색 빈도 컨트롤러도 없고, 그래도 뜬다")
    void startsWithoutSearchBeans() {
        assertThat(context.getBeansOfType(ProductSearchService.class)).isEmpty();
        assertThat(context.getBeansOfType(InternalProductSearchController.class)).isEmpty();
    }
}
