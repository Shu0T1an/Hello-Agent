/**
 * 冒泡排序函数
 * @param arr 待排序的数字数组
 * @returns 排序后的数组
 */
function bubbleSort(arr: number[]): number[] {
    const n = arr.length;
    // 外层循环控制排序轮数
    for (let i = 0; i < n - 1; i++) {
        // 内层循环进行相邻元素比较和交换
        for (let j = 0; j < n - i - 1; j++) {
            // 如果前一个元素大于后一个元素，交换它们
            if (arr[j] > arr[j + 1]) {
                [arr[j], arr[j + 1]] = [arr[j + 1], arr[j]];
            }
        }
    }
    return arr;
}

// 测试
const arr = [64, 34, 25, 12, 22, 11, 90];
console.log("排序前:", arr);
console.log("排序后:", bubbleSort([...arr]));