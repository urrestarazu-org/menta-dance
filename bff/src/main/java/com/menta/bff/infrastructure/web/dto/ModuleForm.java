package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.CreateVirtualModuleCommand;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualModuleChanges;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the module creation and edit pages of a virtual course (#367). The Virtual API validates no length
 * and its errors name no field, so the title limit (255, the column's) is checked here, with a Spanish message. There
 * is no order field: a new module goes last and the order is changed with the move buttons.
 */
@Getter
@Setter
@NoArgsConstructor
public class ModuleForm {

    @NotBlank(message = "Ingresá el título.")
    @Size(max = 255, message = "El título puede tener hasta 255 caracteres.")
    private String title;

    /** Whether the lessons of the module can be seen without a subscription. */
    private boolean preview;

    /** Prefills the edit form with the module as it is. */
    public static ModuleForm from(VirtualModuleDetail module) {
        ModuleForm form = new ModuleForm();
        form.title = module.title();
        form.preview = module.preview();
        return form;
    }

    /** Builds the creation request; no order, so the API appends the module. Only meaningful once validated. */
    public CreateVirtualModuleCommand toCommand() {
        return new CreateVirtualModuleCommand(title, preview, null);
    }

    /** Builds the change request with both fields of the form; the order is not touched. */
    public VirtualModuleChanges toChanges() {
        return new VirtualModuleChanges(title, preview, null);
    }
}
