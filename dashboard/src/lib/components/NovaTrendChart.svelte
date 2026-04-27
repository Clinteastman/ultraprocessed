<script lang="ts">
  import { onMount, onDestroy } from "svelte";
  import * as echarts from "echarts/core";
  import { LineChart } from "echarts/charts";
  import {
    GridComponent,
    TooltipComponent,
    MarkLineComponent,
    MarkAreaComponent
  } from "echarts/components";
  import { CanvasRenderer } from "echarts/renderers";

  echarts.use([
    LineChart,
    GridComponent,
    TooltipComponent,
    MarkLineComponent,
    MarkAreaComponent,
    CanvasRenderer
  ]);

  interface Bucket {
    label: string;
    novaAverage: number | null;
    kcal: number;
  }

  let { buckets }: { buckets: Bucket[] } = $props();

  let el: HTMLDivElement;
  let chart: echarts.ECharts | undefined;

  // Color the line segment by where it lands on the 1..4 scale; ECharts
  // supports per-point itemStyle but here we keep it simple and color the
  // marker, leaving the line a neutral tint that reads against any band.
  function pointColor(v: number | null): string {
    if (v == null) return "#3A3A3F";
    if (v < 1.5) return "#5BC97D";
    if (v < 2.5) return "#C7C354";
    if (v < 3.5) return "#E8A04A";
    return "#D8543E";
  }

  function render() {
    if (!chart) return;
    const data = buckets.map((b) => b.novaAverage);
    const colors = buckets.map((b) => pointColor(b.novaAverage));
    chart.setOption({
      grid: { top: 16, right: 16, bottom: 28, left: 36 },
      tooltip: {
        trigger: "axis",
        backgroundColor: "#1C1C1F",
        borderColor: "#26262A",
        textStyle: { color: "#F5F4EF" },
        formatter: (params: any[]) => {
          const p = params[0];
          if (p.value == null) {
            return `<div style="font-size:11px;color:#B5B3AC;margin-bottom:4px">${p.name}</div>` +
              `<div style="color:#6E6C66">No data</div>`;
          }
          const idx = p.dataIndex as number;
          const kcal = buckets[idx]?.kcal ?? 0;
          return `<div style="font-size:11px;color:#B5B3AC;margin-bottom:4px">${p.name}</div>` +
            `<div style="font-weight:600">NOVA ${(p.value as number).toFixed(2)}</div>` +
            `<div style="color:#B5B3AC;font-size:11px">${Math.round(kcal)} kcal</div>`;
        }
      },
      xAxis: {
        type: "category",
        data: buckets.map((b) => b.label),
        axisLine: { lineStyle: { color: "#26262A" } },
        axisLabel: { color: "#6E6C66", fontSize: 11 }
      },
      yAxis: {
        type: "value",
        min: 1,
        max: 4,
        interval: 1,
        // Inverted: NOVA 1 at top, NOVA 4 at bottom, so the line going
        // up reads as "diet is improving" rather than the other way round.
        inverse: true,
        splitLine: { lineStyle: { color: "#1C1C1F" } },
        axisLabel: { color: "#6E6C66", fontSize: 11 }
      },
      series: [
        {
          type: "line",
          data,
          connectNulls: true,
          smooth: true,
          showSymbol: true,
          symbolSize: 8,
          itemStyle: { color: (params: any) => colors[params.dataIndex] ?? "#888" },
          lineStyle: { color: "#6E6C66", width: 2 },
          markArea: {
            silent: true,
            itemStyle: { opacity: 0.06 },
            data: [
              [{ yAxis: 1, itemStyle: { color: "#5BC97D" } }, { yAxis: 1.5 }],
              [{ yAxis: 1.5, itemStyle: { color: "#C7C354" } }, { yAxis: 2.5 }],
              [{ yAxis: 2.5, itemStyle: { color: "#E8A04A" } }, { yAxis: 3.5 }],
              [{ yAxis: 3.5, itemStyle: { color: "#D8543E" } }, { yAxis: 4 }]
            ]
          }
        }
      ]
    });
  }

  onMount(() => {
    chart = echarts.init(el);
    render();
    const ro = new ResizeObserver(() => chart?.resize());
    ro.observe(el);
    return () => ro.disconnect();
  });

  onDestroy(() => {
    chart?.dispose();
  });

  $effect(() => {
    void buckets;
    render();
  });
</script>

<div bind:this={el} class="w-full h-48"></div>
