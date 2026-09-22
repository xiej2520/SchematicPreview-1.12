package dev.froyln.schematicpreview.gui;

import java.io.File;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.StringUtils;

import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.data.IconPosition;

/** Small 1.15 MaLiLib screen for editing a directory's preview icon. */
public class DirectoryIconEditScreen extends GuiBase
{
    private final File directory;
    private GuiTextFieldGeneric itemField;
    private IconPosition position = IconPosition.DEFAULT;

    public DirectoryIconEditScreen(File directory)
    {
        this.directory = directory;
        this.title = StringUtils.translate("schematicpreview.title.directory_icon");

        DirectoryIconStore.Entry entry = DirectoryIconStore.get(directory);

        if (entry != null)
        {
            this.position = entry.position;
        }
    }

    @Override
    public void initGui()
    {
        super.initGui();

        int x = this.width / 2 - 140;
        int y = this.height / 2 - 40;
        this.itemField = new GuiTextFieldGeneric(x, y, 280, 18, this.textRenderer);

        DirectoryIconStore.Entry entry = DirectoryIconStore.get(this.directory);

        if (entry != null)
        {
            this.itemField.setText(entry.itemId);
        }

        this.addTextField(this.itemField, null);

        ButtonGeneric save = new ButtonGeneric(x, y + 25, 86, 20,
                StringUtils.translate("malilib.gui.button.save"));
        this.addButton(save, new Action(ActionType.SAVE));

        ButtonGeneric remove = new ButtonGeneric(x + 92, y + 25, 86, 20,
                StringUtils.translate("malilib.gui.button.remove"));
        this.addButton(remove, new Action(ActionType.REMOVE));

        ButtonGeneric cycle = new ButtonGeneric(x + 184, y + 25, 96, 20, this.positionLabel());
        this.addButton(cycle, new Action(ActionType.CYCLE));
    }

    @Override
    protected void drawContents(int mouseX, int mouseY, float partialTicks)
    {
        int x = this.width / 2 - 140;
        int y = this.height / 2 - 64;
        this.drawString(StringUtils.translate("schematicpreview.gui.change_directory_icon"), x, y, COLOR_WHITE);
        this.drawString(StringUtils.translate("schematicpreview.gui.change_directory_icon.pos", this.positionLabel()),
                x, y + 66, COLOR_WHITE);
    }

    private String positionLabel()
    {
        return StringUtils.translate("schematicpreview.position." + this.position.jsonName);
    }

    private void closeToParent()
    {
        this.mc.openScreen(this.getParent());
    }

    private enum ActionType { SAVE, REMOVE, CYCLE }

    private final class Action implements IButtonActionListener
    {
        private final ActionType type;

        private Action(ActionType type)
        {
            this.type = type;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            if (this.type == ActionType.CYCLE)
            {
                position = position.next();
                button.setDisplayString(positionLabel());
                return;
            }

            if (this.type == ActionType.REMOVE)
            {
                DirectoryIconStore.remove(directory);
                closeToParent();
                return;
            }

            if (itemField == null || itemField.getText().trim().isEmpty())
            {
                addMessage(MessageType.ERROR, 3000, "schematicpreview.message.invalid_item_id", "");
                return;
            }

            if (DirectoryIconStore.set(directory, itemField.getText().trim(), position) == false)
            {
                addMessage(MessageType.ERROR, 3000, "schematicpreview.message.invalid_item_id", itemField.getText().trim());
                return;
            }

            closeToParent();
        }
    }
}
