package actions;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class MyAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        NotificationGroup noti = new NotificationGroup("myplugin", NotificationDisplayType.BALLOON, true);
        noti.createNotification(
                "My Title",
                "My Message",
                NotificationType.INFORMATION,
                null)
                .notify(e.getProject());
    }

}
