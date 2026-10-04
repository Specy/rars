SECTIONS {
  . = 0x00400000;
  .text : { *(.text) }
  .text.extra : { *(.text.extra) }
  . = 0x10010000;
  .rodata : { *(.rodata) }
  .data : { *(.data) }
  .bss : { *(.bss) }
  /DISCARD/ : { *(.riscv.attributes) *(.note.GNU-stack) }
}
