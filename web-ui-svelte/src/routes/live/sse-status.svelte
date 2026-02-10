<script>
    import { subscribeSSE, sseStore } from "./sse-client.svelte";
    import { Badge, Popover } from "flowbite-svelte";
    import { DownloadSolid } from "flowbite-svelte-icons";
    import { Chart } from "@flowbite-svelte-plugins/chart";


    /** @import { BadgeProps  } from "flowbite-svelte" */
    /** @import { ApexOptions  } from "apexcharts" */

    /**
     * @type {ApexOptions}
     */
    let chartOptions = {
        colors: ["#1A56DB", "#FDBA8C"],
        series: [
            {
                name: "Messages /s",
                color: "#1A56DB",
                data: [
                    { x: "0", y: 0 },
                    { x: "1", y: 0 },
                    { x: "2", y: 0 }
                ]
            }
        ],
        chart: {
            type: "bar",
            //height: "100px",
            //width: "150px",
            fontFamily: "Inter, sans-serif",
            sparkline: {
                enabled: true
            },
            toolbar: {
                show: false
            }
        },
        plotOptions: {
            bar: {
                horizontal: false,
                columnWidth: "70%",
                borderRadiusApplication: "end",
                borderRadius: 2
            }
        },
        tooltip: {
            shared: true,
            intersect: false,
            style: {
                fontFamily: "Inter, sans-serif"
            }
        },
        states: {
            hover: {
                filter: {
                type: "darken"
                }
            }
        },
        grid: {
            show: false,
            strokeDashArray: 4,
            padding: {
                left: 2,
                right: 2,
                top: -14
            }
        },
        dataLabels: {
            enabled: false
        },
        legend: {
            show: false
        },
        xaxis: {
            floating: false,
            labels: {
                show: false,
                style: {
                    fontFamily: "Inter, sans-serif",
                    cssClass: "text-xs font-normal fill-gray-500 dark:fill-gray-400"
                }
            },
            axisBorder: {
                show: true
            },
            axisTicks: {
                show: false
            }
        },
        yaxis: {
            show: true,
            axisBorder: {
                show: true
            },
        },
        fill: {
            opacity: 1
        }
    };

    /**
     * @type {BadgeProps["color"]}
     */
    let connectionBadgeColor = $derived.by(() => {
        if (sseStore.status === "connected") {
            return "green";
        } else if (sseStore.status === "connecting") {
            return "yellow";
        } else {
            return "gray";
        }
    });


    /**
     * @type {number[]}
     */
    let messagesPerSecond = $state([]);

    let messagesPerSecondAverage = $derived.by(() => {
        let sum = 0;
        for (let i = 0; i < messagesPerSecond.length; i++) {
            sum += messagesPerSecond[i];
        }
        return sum / messagesPerSecond.length;    
    });

    let messageCounter = 0;

    /**
     * @param {number} count
     */
    function logMessagesPerSecond(count) {
        messagesPerSecond.push(count);

        if (messagesPerSecond.length >= 20) {
            messagesPerSecond.shift();
        }
    }

    /*
    * Subscribe to SSE messages
    */
    subscribeSSE((message) => {
        messageCounter++;
    });

    function updateChartData() {
        /**
		 * @type {{ x: string; y: number; }[]}
		 */
        let newData = [];
        let xIndex = 1 - messagesPerSecond.length;
        
        messagesPerSecond.forEach(element => {
            newData.push({ x: xIndex.toString() + " s", y: element });
            xIndex++;
        });

        chartOptions.series[0].data = newData; 
        // Create a completely new options object to trigger reactivity
        /*
        chartOptions = {
            ...chartOptions,
            series: [
                {
                name: "Messages /s",
                color: "#1A56DB",
                data: newData
                }
            ]
        };
        */
        //console.log("Update chart data: ", newData);   
    }

    /*
    * Log number of messages per second
    */
    setInterval(() => {
        logMessagesPerSecond(messageCounter);
        messageCounter = 0; // Reset for the next second
        updateChartData();
    }, 1000);

</script>

<div >
    <Badge color={connectionBadgeColor} border>
        <DownloadSolid class="me-1.5 h-2.5 w-2.5" />
        {sseStore.status}
    </Badge>
    <Popover class="w-48 text-sm font-light" >
        <div class="space-y-2">
            <h3 class="font-semibold text-gray-900 dark:text-white">Messages /s: {messagesPerSecondAverage.toFixed(2)}</h3>
        </div>
        <div class="pt-4">
            <Chart bind:options={chartOptions} />
        </div>
    </Popover >

</div>