import { useState, useEffect } from 'react';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import type { Action } from '../../types/workflow';
import { GripVertical, Trash2 } from 'lucide-react';

interface ActionsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  workflowName: string;
  actions: Action[];
  onSave: (actions: Action[]) => Promise<void>;
}

interface SortableActionItemProps {
  action: Action;
  index: number;
  onDelete: (index: number) => void;
}

const SortableActionItem = ({ action, index, onDelete }: SortableActionItemProps) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: `action-${index}` });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`flex items-center gap-3 p-3 bg-card border rounded-lg ${
        isDragging ? 'shadow-lg ring-2 ring-primary' : 'hover:bg-muted/50'
      }`}
    >
      <button
        {...attributes}
        {...listeners}
        className="cursor-grab active:cursor-grabbing text-muted-foreground hover:text-foreground touch-none"
      >
        <GripVertical className="w-5 h-5" />
      </button>

      <div className="flex-shrink-0 w-6 h-6 flex items-center justify-center text-xs font-medium text-muted-foreground bg-secondary rounded">
        {index + 1}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <span className="text-sm font-medium capitalize">{action.type}</span>
          {action.type === 'click' && (
            <span className="px-1.5 py-0.5 text-xs bg-blue-100 text-blue-700 rounded">
              Click
            </span>
          )}
          {action.type === 'fill' && (
            <span className="px-1.5 py-0.5 text-xs bg-green-100 text-green-700 rounded">
              Fill
            </span>
          )}
          {action.type === 'navigate' && (
            <span className="px-1.5 py-0.5 text-xs bg-purple-100 text-purple-700 rounded">
              Navigate
            </span>
          )}
        </div>
        <div className="text-xs font-mono text-muted-foreground truncate">
          {action.selector || action.value || 'N/A'}
        </div>
        {action.value && action.selector && (
          <div className="text-xs text-muted-foreground mt-0.5">
            Value: "{action.value}"
          </div>
        )}
      </div>

      <Button
        variant="ghost"
        size="icon"
        onClick={() => onDelete(index)}
        className="text-destructive hover:text-destructive hover:bg-destructive/10"
      >
        <Trash2 className="w-4 h-4" />
      </Button>
    </div>
  );
};

export const ActionsDialog = ({
  open,
  onOpenChange,
  workflowName,
  actions: initialActions,
  onSave,
}: ActionsDialogProps) => {
  const [actions, setActions] = useState<Action[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [hasChanges, setHasChanges] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  useEffect(() => {
    if (open) {
      setActions([...initialActions]);
      setHasChanges(false);
    }
  }, [open, initialActions]);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;

    if (over && active.id !== over.id) {
      setActions((items) => {
        const oldIndex = parseInt(active.id.toString().split('-')[1]);
        const newIndex = parseInt(over.id.toString().split('-')[1]);
        const newItems = arrayMove(items, oldIndex, newIndex);
        setHasChanges(true);
        return newItems;
      });
    }
  };

  const handleDelete = (index: number) => {
    setActions((items) => items.filter((_, i) => i !== index));
    setHasChanges(true);
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await onSave(actions);
      setHasChanges(false);
      onOpenChange(false);
    } catch (error) {
      console.error('Failed to save actions:', error);
      alert('Failed to save actions: ' + (error instanceof Error ? error.message : 'Unknown error'));
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancel = () => {
    if (hasChanges && !confirm('Discard changes?')) {
      return;
    }
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px] max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>Actions in "{workflowName}"</DialogTitle>
          <DialogDescription>
            Drag and drop to reorder actions. Click the trash icon to delete.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto pr-2 -mr-2">
          {actions.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <svg
                className="w-12 h-12 text-muted mb-3"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1}
                  d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                />
              </svg>
              <p className="text-sm text-muted-foreground">No actions in this workflow</p>
            </div>
          ) : (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={actions.map((_, idx) => `action-${idx}`)}
                strategy={verticalListSortingStrategy}
              >
                <div className="space-y-2">
                  {actions.map((action, index) => (
                    <SortableActionItem
                      key={`action-${index}`}
                      action={action}
                      index={index}
                      onDelete={handleDelete}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>
          )}
        </div>

        <DialogFooter className="border-t pt-4 mt-4">
          <div className="flex items-center justify-between w-full">
            <div className="text-sm text-muted-foreground">
              {actions.length} {actions.length === 1 ? 'action' : 'actions'}
              {hasChanges && ' • Unsaved changes'}
            </div>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={handleCancel}
                disabled={isSaving}
              >
                Cancel
              </Button>
              <Button
                onClick={handleSave}
                disabled={!hasChanges || isSaving}
              >
                {isSaving ? 'Saving...' : 'Save Changes'}
              </Button>
            </div>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
