.option norvc
.option norelax
.text
.globl main
main:
    auipc t0,%pcrel_hi(table)
    addi t0,t0,%pcrel_lo(main)
    lw a0,0(t0)
    addi a7,zero,93
    ecall
.p2align 3
.section .text.extra,"ax",@progbits
extra: addi zero,zero,0
.section .data,"aw",@progbits
byte: .byte 127
unaligned: .word 0x11223344
pointer: .quad table+4
.section .rodata,"a",@progbits
table: .word 42
bytes: .ascii "\017\000\377"
floating: .float 1.0,-0.0
doublevalue: .double 3.25
.section .data
resumed: .byte 128
.section .bss,"aw",@nobits
.balign 16
zeroed: .zero 12
