package app.trashbin.storage.web;

import app.trashbin.storage.StorageReconciler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operations endpoints. Restricted to ADMIN by the security configuration (/api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin/storage")
public class StorageAdminController {

    private final StorageReconciler reconciler;

    public StorageAdminController(StorageReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @PostMapping("/reconcile")
    public StorageReconciler.Report reconcile() {
        return reconciler.reconcile();
    }
}
