package com.youthbenefit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTextTest {

	@Test
	void turnsHtmlSymbolsBackIntoCharacters() {
		assertThat(HtmlText.unescape("등록된 &apos;공인&apos;자격증, &quot;부분공인&quot;")).isEqualTo("등록된 '공인'자격증, \"부분공인\"");
		assertThat(HtmlText.unescape("교육&middot;컨설팅 &lt;강연&gt; A&amp;B &#39;x&#39; &#x2192;")).isEqualTo("교육·컨설팅 <강연> A&B 'x' →");
	}

	@Test
	void leavesUnknownSymbolsAndPlainTextAlone() {
		assertThat(HtmlText.unescape("R&D 지원 &foo; &#0;")).isEqualTo("R&D 지원 &foo; &#0;");
		assertThat(HtmlText.unescape(null)).isNull();
	}

	@Test
	void unescapesOnlyOnce() {
		assertThat(HtmlText.unescape("&amp;apos;")).isEqualTo("&apos;");
	}

}
