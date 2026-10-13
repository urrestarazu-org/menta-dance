package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@DisplayName("PhysicalBankTransferController")
class PhysicalBankTransferControllerTest {

    @Test
    @DisplayName("a file over the upload limit becomes a Spanish 400 notice that leads back to the instructions")
    void tooBig() {
        PhysicalBankTransferController controller = new PhysicalBankTransferController(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.proofTooBig(response, model);

        assertThat(view).isEqualTo("purchase-notice");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.getAttribute("message").toString()).contains("5 MB");
        assertThat(model.getAttribute("linkHref")).isEqualTo("/compras/transferencia");
        assertThat(MaxUploadSizeExceededException.class).isNotNull();
    }
}
