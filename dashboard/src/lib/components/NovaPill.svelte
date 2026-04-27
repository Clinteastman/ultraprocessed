<script lang="ts">
  let { novaClass }: { novaClass: number } = $props();

  const colors = ["#5BC97D", "#5BC97D", "#C7C354", "#E8A04A", "#D8543E"];
  const color = $derived(colors[Math.max(0, Math.min(4, novaClass))]);
</script>

<span
  class="inline-flex items-center rounded-sm px-2 py-0.5 text-[11px] font-medium tracking-wider uppercase"
  style="background-color: color-mix(in srgb, {color} 18%, transparent); color: {color};"
>
  NOVA {novaClass}
</span>
