# Logseq Outliner Block Tree Engine Diagram Pack

This file is a reconstruction-oriented diagram set for Logseq's outliner subsystem.
It is meant to complement the private reverse-engineering writeup in `Downloads`, not replace it.

Primary source artifacts:
- `deps/outliner/src/logseq/outliner/core.cljs`
- `deps/outliner/src/logseq/outliner/tree.cljs`
- `deps/outliner/src/logseq/outliner/op.cljs`
- `deps/outliner/src/logseq/outliner/validate.cljs`
- `deps/db/src/logseq/db/common/order.cljs`
- `deps/db/src/logseq/db.cljs`

Important accuracy notes:
- Blocks do not inherit from pages. Tree structure is reference-based through `:block/parent`, and page ownership is reference-based through `:block/page`.
- `move-blocks` uses per-block `ldb/transact!` calls inside batch mode rather than one guaranteed DB transaction for the whole visible operation.
- Direct outdent can trigger a second internal move for right siblings.

## 1. Structural View

```mermaid
classDiagram
    class Block {
        +UUID uuid
        +Ref parent
        +Ref page
        +String order
        +String title
        +String name
        +Boolean collapsed
        +Long createdAt
        +Long updatedAt
    }

    class Page {
        +UUID uuid
        +String name
        +String title
    }

    class TreeNode {
        +Block block
        +List~TreeNode~ children
        +Int level
    }

    class INode {
        <<protocol>>
        +save(txsState, conn, opts)
        +del(txsState, db)
    }

    class OutlinerCore {
        +saveBlock(block, opts)
        +insertBlocks(blocks, target, opts)
        +deleteBlocks(blocks, opts)
        +moveBlocks(blocks, target, opts)
        +moveBlocksUpDown(blocks, up)
        +indentOutdentBlocks(blocks, indent, opts)
    }

    class ApplyOps {
        +applyOps(conn, ops, opts)
    }

    class TreeBuilder {
        +blocksToVecTree(db, blocks, rootId)
        +nonConsecutiveBlocksToVecTree(blocks)
    }

    class OrderService {
        +genKey(left, right)
        +genNKeys(count, left, right)
    }

    class DBHelpers {
        +entity(id)
        +getLeftSibling(block)
        +getRightSibling(block)
        +getDown(block)
        +getBlockParents(blockUuid, opts)
    }

    class ValidationRules {
        +validatePageTitleCharacters(pageTitle, meta)
        +validatePageTitle(pageTitle, meta)
        +validateBlockTitle(db, newTitle, entity)
    }

    class DataScriptDB {
        +transact(txData, meta)
    }

    Block --> Block : parent ref
    Block --> Page : page ref
    TreeNode --> Block : wraps
    ApplyOps --> OutlinerCore : dispatches
    OutlinerCore --> INode : save/delete through
    OutlinerCore --> TreeBuilder
    OutlinerCore --> OrderService
    OutlinerCore --> DBHelpers
    OutlinerCore --> ValidationRules
    OutlinerCore --> DataScriptDB
```

## 2. Operation Pipeline

```mermaid
sequenceDiagram
    actor User
    participant UI as Editor UI
    participant Ops as applyOps
    participant Core as OutlinerCore
    participant Validate as ValidationRules
    participant Helpers as DBHelpers
    participant Order as OrderService
    participant Store as DataScriptDB
    participant Tree as TreeBuilder

    User->>UI: Enter / Tab / Shift+Tab / Paste / Drag
    UI->>Ops: operation tuple(s)
    Ops->>Core: dispatch operation
    Core->>Validate: run title / graph validations as needed
    Core->>Helpers: load target, siblings, parents, page context
    Helpers-->>Core: structural context
    Core->>Order: generate order key(s)
    Order-->>Core: sortable fractional keys
    alt single-tx style operation
        Core->>Store: transact(txData, meta)
    else batch-mode or follow-on structural operation
        loop one or more low-level transacts
            Core->>Store: transact(txData, meta)
        end
    end
    Store-->>Tree: flat block entities available
    Tree->>Tree: group by parent
    Tree->>Tree: sort by order
    Tree->>Tree: assign levels and children
    Tree-->>UI: nested vec tree
```

## 3. Insert And Move Flow

```mermaid
flowchart TD
    A["Insert or move request"] --> B{"Operation type?"}

    B -->|"Insert"| C["Normalize incoming blocks"]
    C --> D{"Keep UUIDs?"}
    D -->|"No"| E["Generate fresh UUID map"]
    D -->|"Yes"| F["Preserve UUIDs"]
    E --> G["Remap internal references and parents"]
    F --> G
    G --> H["Resolve target block and sibling/child mode"]
    H --> I["Determine left/right order bounds"]
    I --> J["Generate one or more order keys"]
    J --> K["Assign parent, page, order, timestamps"]
    K --> L["Build insert tx data"]

    B -->|"Move"| M["Filter to top-level blocks"]
    M --> N{"Move is no-op?"}
    N -->|"Yes"| O["Skip transaction"]
    N -->|"No"| P["Resolve destination parent and sibling mode"]
    P --> Q["Compute destination order key"]
    Q --> R["Rewrite parent for moved block"]
    R --> S{"Cross-page move?"}
    S -->|"Yes"| T["Rewrite page for moved block and descendants"]
    S -->|"No"| U["Keep page refs"]
    T --> V["Emit move tx data"]
    U --> V

    L --> W["Persist change"]
    V --> X["Persist per moved top-level block in batch mode"]
```

## 4. Indent And Outdent Flow

```mermaid
flowchart TD
    A["Indent or outdent request"] --> B{"Mode?"}

    B -->|"Indent"| C["Find left sibling"]
    C --> D{"Left sibling exists?"}
    D -->|"No"| E["Abort"]
    D -->|"Yes"| F{"Left sibling already has children?"}
    F -->|"Yes"| G["Target last direct child and move as sibling after it"]
    F -->|"No"| H["Move block as first child of left sibling"]
    G --> I{"Left sibling collapsed?"}
    H --> I
    I -->|"Yes"| J["Expand left sibling"]
    I -->|"No"| K["Keep collapse state"]
    J --> L["Persist indent result"]
    K --> L

    B -->|"Outdent"| M["Find current parent"]
    M --> N{"Parent exists?"}
    N -->|"No"| E
    N -->|"Yes"| O["Move block after its current parent"]
    O --> P{"Logical outdenting?"}
    P -->|"Yes"| Q["Stop after primary move"]
    P -->|"No"| R["Find right siblings of outdented block"]
    R --> S{"Right siblings exist?"}
    S -->|"No"| Q
    S -->|"Yes"| T["Move right siblings under outdented block"]
    T --> U["Persist follow-on move"]
```
